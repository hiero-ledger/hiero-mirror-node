# `StackedStateFrames` and `UpdatableReferenceCache`

## The `StackedStateFrames` for caching objects during contract execution

## Overview

The purpose of this is to support `eth_call` and `eth_estimateGas`, both of which can update domain objects while executing contracts, but that updated state is never persisted past a contract's execution (and certainly _not_ to the database).

As each contract executes its changes to state must be kept: later execution may refer to the same object and want the updated state. This also applies as contracts call other contracts. However, in a call to another contract, that call can either succeed or fail. In the success case changes to objects made by the _called_ contract are kept so that the caller can see them. But in the failure case ("revert") those changes must be thrown away so that the caller does _not_ see them.

The `eth_estimateGas` call is more complicated: It must do a search for the proper amount of gas (for various complicated reasons) and it does that by calling the contract multiple times with the same arguments but different amount of allowable gas, in order to search for the closest gas estimate it can find that works. Each of those calls must access the same state no matter how many blocks have come in during the multiple executions of the same contract.

## The Stacked State Frames: Principles of Operation

A stacked state frames is a multi-level cache that provides "layered" "frame-based" caching with limited writes being allowed upstream.

The most **up**stream cache is simply a pass-through-only accessor of ground truth - usually to a database. Given a key it will call the accessor and return whatever is the there - either an actual value, or "missing".

The database layer does no caching at all, but stacked on top of it is a _read only_ cache that passed through missing reads to the upstream database layer and then holds the returned value. All subsequent reads of that key will be returned directly from the RO cache.

These two layers form the stack base. This base is _reused_ over the multiple-calls to the same contract with the same parameters and the same state during a single call to `eth_estimateGas`.

Above the stack base comes read-write cache levels, one for each contract call in progress. They're like call-frames, but hold only state objects. As each nested contract call is made a new empty one is pushed onto the stack, using the previous top-of-stack as its upstream cache.

At any time the cache can return values that it holds, or, if it knows that it has never been asked for a value, it will call its upstream asking for that value, and on return, store it.

When a contract call completes successfully you commit its state changes to its upstream. If the contract call fails you simply pop the top of the stack and throw it away. You never commit to the stack base (the RO cache frame there) - or if you do, it will fail.

## What are the allowable states of a cache line?

Each frame of the cache will track key/value pairs according to the following states:

|       State       | Description                                                                                                                                |
| :---------------: | :----------------------------------------------------------------------------------------------------------------------------------------- |
| `NOT_YET_FETCHED` | The key has not yet be requested at this cache level.<br>Immediately defer to the upstream cache to see what<br>it thinks.                 |
|     `MISSING`     | There's no value for this key in the upstream cache<br>(we've already looked).                                                             |
|     `PRESENT`     | Value exists in the upstream cache, here it is. It hasn't<br> been changed at this level.                                                  |
|     `UPDATED`     | Value has been updated here at this level. (Don't<br>know at this point if it existed upstream: It could<br>be new.)                       |
|     `DELETED`     | Value has been deleted at this level. May have existed<br>upstream, or, may have been created here and then<br>deleted at this same level. |

(There's also an `INVALID` state that would indicate a logic error within the cache if you ever ran across it.)

### Important Usage Note:

This _wants_ to be a cache of _values_ but in fact is a cache of _references_ - the user must be _cautious_!

These caches cache references to things. They should really, really only be used to cache immutable value-like entities. Otherwise, if you actually modify an entities' state you'll modify it not only in the top-level cache but in the lower-caches _which breaks the "commit/revert" feature_. The implementation will attempt to detect, at runtime, that you're trying to update an entry with the same value (i.e., reference) that's already in the cache - which probably indicates that you modified the state and are attempting to update the cache with it - but it can't prevent you from just modifying the thing in place and _not_ updating it in the cache.

- An alternate solution would be to immediately wrap underlying R/W entities in R/O wrappers and then use them exclusively.

## Thread safety

The stacked state frames design is thread-safe in the sense that the in memory state can be used by multiple threads at the same time. That is achieved by wrapping stack and stack base into ThreadLocal instances. In this way each thread gets its own version of the stack and could modify it, without contaminating other threads/calls. This is possible, since each call is handled by a separate single thread.

For simple single calls, such as `eth_call` scenarios the stack and stack base fields are cleared directly after the execution of the call. For cases, where we iterate multiple times over the same call, such as `eth_estimateGas` we clear only the stack since we want to reuse the cached values into stack base for all iterations of the binary search algorithm. Then, after we calculate the gas estimation, we also clear the stack base.

## A paragraph (or two) on the _type-safety_ of the cache and the use of Java generics

The use case for the stacked state frames includes _type-safe_ use of a cache capable of holding several hetrogenous (and unrelated-to-each-other) entity types. Further: that it be easy to expand the set of entity types as features are developed (or to change one entity type out for another similar one).

These use cases suggest the use of Java generics, however, Java generics have several limitations that make this far from seamless, in particular both implementation by _type erasure_ (which means that generic type parameters are completely unknown at runtime) and lack of variadic type parameters for generics.

- To make matters more interesting, of the three operations done on entities in a cache - `get`, `set`, and `delete`, only `set` provides a entity type on call. `get` returns an entity type but that isn't useful for enforcing type safety and `delete` doesn't involve an entity type in its signature at all.

After trying a large number of variations this is the solution I came up with:

- All classes use _Object_ as a key type, so that we can use different type of keys for different accessors. It's not possible to use a generic type, since the main fields which are stack and stack base are wrapped in ThreadLocal instances.
- Each entity type is segregated into its own "cache" - an `UpdatableReferenceCache`.
  - The `UpdatableReferenceCache` is actually holding `Objects` and relies on its users (owner classes) to make sure that only one kind of entity is stored in it.
- A `CachingStateFrame` can hold multiple entity types - each one gets its own `UpdatableReferenceCache`.
  - The entity types a `CachingStateFrame` can hold are specified at construction time by having the list of entity classes passed in to it.
    - It then creates the needed `UpdatableReferenceCache`s to hold those entity types, and holds them in a map indexed by the `Class` instance.
  - To ensure type-safety all accesses (`get`, `set`, and `delete` are done by _accessors_ available from the `CachingStateFrame`. You ask for one of those for the specific type you're interested in accessing (by giving its `Class` instance) and then you have fully type-safe access to `get`, `set`, and `delete` on those kinds of entities in that frame.
    - It's inexpensive to get one of these accessors because they're created when the `CachingStateFrame` is constructed and then those instances are just used and reused whenever asked for.

## A few diagrams to help this description out

### Object diagram for a contract call with an inner call

![Object diagram for a contract call with an inner call](images/ObjectDiagram-StackedStateFrames.svg)

<div style="page-break-before:always"></div>

### Class diagram of `StackedStateFrames` components

![Class diagram of StackedStateFrame components ](images/ClassDiagram-StackedStateFrames.svg)

<div style="page-break-before:always"></div>

### State diagram for `UpdatableReferenceCache`

![State diagram for UpdatableReferenceCache](images/StateDiagram-UpdatableReferenceCache.svg)

<div style="page-break-before:always"></div>

### Thread workflow diagram for `StackedStateFrames`

![Thread workflow diagram for StackedStateFrames](images/ThreadDiagram-StackedStateFrames.png)

<div style="page-break-before:always"></div>
### Decision diagram for `UpdateableReferenceCache`

#### _(method,state) ⮕ action_

![Decision diagram](images/DecisionDiagram-UpdatableReferenceCache.png)
