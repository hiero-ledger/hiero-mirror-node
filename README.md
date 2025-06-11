# Continuous Deployment

This branch contains declarative configuration that represents the desired state of all Hedera managed mirror node environments.
The practice of using Git as a single source of truth for declarative infrastructure and applications is called [GitOps](https://www.gitops.tech).
To this end, we use [Flux](https://fluxcd.io) in our Kubernetes clusters to manage our deploys.

## Install

### Kubernetes

Create a standard GKE cluster with at least Kubernetes 1.32.

### Sealed Secrets

```bash
brew install kubeseal ksd yq
helm upgrade -i --wait --create-namespace -n flux-system sealed-secrets oci://registry-1.docker.io/bitnamicharts/sealed-secrets --set keyrenewperiod=0s
```

### FluxCD

```bash
export GITHUB_TOKEN=...
export CONTEXT=preprod
brew install fluxcd/tap/flux
flux check --pre
flux bootstrap github --owner=hiero-ledger --repository=hiero-mirror-node --branch=deploy --team=hiero-mirror-node-committers --context="${CONTEXT}" --path="clusters/${CONTEXT}" --private=false --interval=1m
```

### GitHub

#### GitHub Dispatch

For alerts of type [GitHub Dispatch](https://fluxcd.io/flux/components/notification/providers/#github-dispatch), a new GitHub
[Personal Access Token (PAT)](https://github.com/settings/personal-access-tokens/new) will need to be created with repository
permissions for `Contents` scope with `Read and Write` access. This PAT is created under the
[hedera-github-bot](https://github.com/hedera-github-bot) user. The resource owner should be the hiero-ledger organization and
have repository access only for `hiero-ledger/hiero-mirror-node`.

#### GitHub Commit Status

For alerts of type [GitHub](https://fluxcd.io/flux/components/notification/providers/#git-commit-status-updates) that
update the git commit status on GitHub, a new GitHub PAT will need to be created with repository permissions for
`Commit statuses` scope with `Read and Write` access. This PAT is created under the
[hedera-github-bot](https://github.com/hedera-github-bot) user. The resource owner should be the hiero-ledger 
organization and have repository access only for `hiero-ledger/hiero-mirror-node`.

## Configure

If you're adding a new secret, skip to the next step. If you're updating an existing sealed secret, first retrieve the unecrypted secret from the cluster:

```bash
kubectl get secret mirror -o yaml | ksd | yq 'del(.metadata.ownerReferences, .metadata.resourceVersion, .metadata.uid, .metadata.creationTimestamp)' > secret.yaml
```

Use the `kubseal` command to connect to the in-cluster sealed-secrets controller to encrypt local secrets, then commit the sealed secret to git.

```bash
kubeseal --controller-namespace=flux-system --controller-name=sealed-secrets -o yaml <secret.yaml >sealed-secret.yaml
git add sealed-secret.yaml
```

## Upgrade

```bash
brew upgrade
flux install --export > ./clusters/${CONTEXT}/flux-system/gotk-components.yaml
```

## Restore

If just a particular workload or a namespace has problems, Flux should automatically reconcile it to restore it to its proper state.
If the entire cluster is corrupted or lost, it will need to be restored manually using the below instructions.
Since everything but the database and the sealed secrets private key is stored in git, restoring the cluster is pretty straight forward.
Simply install sealed secrets, restore the backed up private key then install Flux and the cluster will reconstitute itself.

1. Install [Sealed Secrets](#sealed-secrets)
2. `kubectl scale -n flux-system --replicas=0 deployment sealed-secrets`
3. `kubectl delete secret -n flux-system -l sealedsecrets.bitnami.com/sealed-secrets-key=active`
4. `kubectl apply -f <backed up sealed-secrets key>`
5. `kubectl scale -n flux-system --replicas=1 deployment sealed-secrets`
6. Install [Flux](#flux-v2)

## Testing

minikube start
kubectl create ns test
flux install
flux create source helm hedera-mirror-node --url=https://hiero-ledger.github.io/hiero-mirror-node/charts -n test
flux create helmrelease mirror -n test --source=HelmRepository/hedera-mirror-node --chart=hedera-mirror

