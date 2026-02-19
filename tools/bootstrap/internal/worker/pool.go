// SPDX-License-Identifier: Apache-2.0

// Package worker provides a concurrent job processing pool for parallel imports.
package worker

import (
	"context"
	"sync"
	"sync/atomic"
)

// Job represents a single import job.
type Job struct {
	Filename string // Base filename
	FilePath string // Full path to file
	Index    int    // Job index for ordering
}

// Result contains the outcome of a job.
type Result struct {
	Job              Job
	Success          bool
	RowsImported     int64
	ExpectedRows     int64 // Expected rows from manifest
	RowCountMismatch bool  // True if imported != expected
	Error            error
}

// Pool manages concurrent job processing with configurable parallelism.
type Pool struct {
	workers int
	jobs    chan Job
	results chan Result
	wg      sync.WaitGroup
	ctx     context.Context
	cancel  context.CancelFunc

	// Metrics
	totalSubmitted int64
	totalCompleted int64
	totalFailed    int64
}

// NewPool creates a new worker pool with the specified number of workers.
func NewPool(ctx context.Context, workers int) *Pool {
	ctx, cancel := context.WithCancel(ctx)
	return &Pool{
		workers: workers,
		jobs:    make(chan Job, workers*2),      // Buffered for smooth flow
		results: make(chan Result, workers*2),
		ctx:     ctx,
		cancel:  cancel,
	}
}

// Start begins processing jobs with the provided processor function.
// The processor function is called for each job in a separate goroutine.
func (p *Pool) Start(processor func(context.Context, Job) Result) {
	for i := 0; i < p.workers; i++ {
		p.wg.Add(1)
		go func(workerID int) {
			defer p.wg.Done()
			for {
				select {
				case job, ok := <-p.jobs:
					if !ok {
						return
					}
					result := processor(p.ctx, job)
					atomic.AddInt64(&p.totalCompleted, 1)
					if !result.Success {
						atomic.AddInt64(&p.totalFailed, 1)
					}
					// Send result (may block if results buffer full)
					select {
					case p.results <- result:
						
					case <-p.ctx.Done():
						return
					}
				case <-p.ctx.Done():
					return
				}
			}
		}(i)
	}
}

// Submit adds a job to the pool for processing.
// Blocks if the job buffer is full.
func (p *Pool) Submit(job Job) bool {
	select {
	case p.jobs <- job:
		atomic.AddInt64(&p.totalSubmitted, 1)
		return true
	case <-p.ctx.Done():
		return false
	}
}

// Results returns the channel for receiving job results.
func (p *Pool) Results() <-chan Result {
	return p.results
}

// Close stops accepting new jobs and waits for all workers to finish.
// Results channel is closed after all workers complete.
func (p *Pool) Close() {
	close(p.jobs)
	p.wg.Wait()
	close(p.results)
}

// Shutdown cancels all pending work and closes the pool.
func (p *Pool) Shutdown() {
	p.cancel()
	p.Close()
}

// Metrics returns pool statistics.
type Metrics struct {
	Submitted int64
	Completed int64
	Failed    int64
	Pending   int64
}

func (p *Pool) Metrics() Metrics {
	submitted := atomic.LoadInt64(&p.totalSubmitted)
	completed := atomic.LoadInt64(&p.totalCompleted)
	return Metrics{
		Submitted: submitted,
		Completed: completed,
		Failed:    atomic.LoadInt64(&p.totalFailed),
		Pending:   submitted - completed,
	}
}

// Workers returns the number of workers in the pool.
func (p *Pool) Workers() int {
	return p.workers
}

// Done returns a channel that's closed when the context is cancelled.
func (p *Pool) Done() <-chan struct{} {
	return p.ctx.Done()
}
