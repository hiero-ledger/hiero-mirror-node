// SPDX-License-Identifier: Apache-2.0

package worker

import (
	"context"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestPoolBasic(t *testing.T) {
	ctx := context.Background()
	pool := NewPool(ctx, 2)

	var processed int64

	// Start workers
	pool.Start(func(ctx context.Context, job Job) Result {
		atomic.AddInt64(&processed, 1)
		return Result{
			Job:          job,
			Success:      true,
			RowsImported: 100,
		}
	})

	// Submit jobs
	for i := 0; i < 5; i++ {
		pool.Submit(Job{
			Filename: "file" + string(rune('0'+i)) + ".csv.gz",
			FilePath: "/path/file" + string(rune('0'+i)) + ".csv.gz",
			Index:    i,
		})
	}

	// Collect results
	go func() {
		pool.Close()
	}()

	resultCount := 0
	for range pool.Results() {
		resultCount++
	}

	if resultCount != 5 {
		t.Errorf("Expected 5 results, got %d", resultCount)
	}

	if processed != 5 {
		t.Errorf("Expected 5 processed, got %d", processed)
	}
}

func TestPoolConcurrency(t *testing.T) {
	ctx := context.Background()
	workers := 4
	pool := NewPool(ctx, workers)

	var maxConcurrent int64
	var currentConcurrent int64
	var mu sync.Mutex

	pool.Start(func(ctx context.Context, job Job) Result {
		current := atomic.AddInt64(&currentConcurrent, 1)
		mu.Lock()
		if current > maxConcurrent {
			maxConcurrent = current
		}
		mu.Unlock()

		time.Sleep(10 * time.Millisecond) // Simulate work

		atomic.AddInt64(&currentConcurrent, -1)
		return Result{Job: job, Success: true}
	})

	// Submit more jobs than workers
	for i := 0; i < 20; i++ {
		pool.Submit(Job{Index: i})
	}

	go func() {
		pool.Close()
	}()

	// Drain results
	for range pool.Results() {
	}

	if maxConcurrent > int64(workers) {
		t.Errorf("Max concurrent %d exceeded workers %d", maxConcurrent, workers)
	}
}

func TestPoolMetrics(t *testing.T) {
	ctx := context.Background()
	pool := NewPool(ctx, 2)

	pool.Start(func(ctx context.Context, job Job) Result {
		// Fail odd indices
		success := job.Index%2 == 0
		return Result{Job: job, Success: success}
	})

	for i := 0; i < 6; i++ {
		pool.Submit(Job{Index: i})
	}

	go func() {
		pool.Close()
	}()

	// Drain results
	for range pool.Results() {
	}

	metrics := pool.Metrics()

	if metrics.Submitted != 6 {
		t.Errorf("Expected 6 submitted, got %d", metrics.Submitted)
	}
	if metrics.Completed != 6 {
		t.Errorf("Expected 6 completed, got %d", metrics.Completed)
	}
	if metrics.Failed != 3 {
		t.Errorf("Expected 3 failed, got %d", metrics.Failed)
	}
}

func TestPoolShutdown(t *testing.T) {
	ctx := context.Background()
	pool := NewPool(ctx, 2)

	var processed int64

	pool.Start(func(ctx context.Context, job Job) Result {
		select {
		case <-time.After(100 * time.Millisecond):
			atomic.AddInt64(&processed, 1)
			return Result{Job: job, Success: true}
		case <-ctx.Done():
			return Result{Job: job, Success: false, Error: ctx.Err()}
		}
	})

	// Submit some jobs
	for i := 0; i < 10; i++ {
		pool.Submit(Job{Index: i})
	}

	// Shutdown quickly
	time.Sleep(50 * time.Millisecond)
	pool.Shutdown()

	// Should have processed fewer than submitted
	if processed >= 10 {
		t.Errorf("Expected fewer than 10 processed after shutdown, got %d", processed)
	}
}

func TestPoolContext(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	pool := NewPool(ctx, 2)

	pool.Start(func(ctx context.Context, job Job) Result {
		time.Sleep(50 * time.Millisecond)
		return Result{Job: job, Success: true}
	})

	// Submit job
	pool.Submit(Job{Index: 0})

	// Cancel context
	cancel()

	// Should not block on close
	done := make(chan struct{})
	go func() {
		pool.Close()
		close(done)
	}()

	select {
	case <-done:
		// Good
	case <-time.After(1 * time.Second):
		t.Error("Pool.Close blocked after context cancellation")
	}
}

func TestPoolWorkerCount(t *testing.T) {
	ctx := context.Background()
	pool := NewPool(ctx, 8)

	if pool.Workers() != 8 {
		t.Errorf("Expected 8 workers, got %d", pool.Workers())
	}
}

func BenchmarkPoolSubmit(b *testing.B) {
	ctx := context.Background()
	pool := NewPool(ctx, 4)

	pool.Start(func(ctx context.Context, job Job) Result {
		return Result{Job: job, Success: true}
	})

	go func() {
		for range pool.Results() {
		}
	}()

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		pool.Submit(Job{Index: i})
	}

	pool.Close()
}

func BenchmarkPoolThroughput(b *testing.B) {
	ctx := context.Background()
	pool := NewPool(ctx, 8)

	var processed int64

	pool.Start(func(ctx context.Context, job Job) Result {
		atomic.AddInt64(&processed, 1)
		return Result{Job: job, Success: true, RowsImported: 1000}
	})

	go func() {
		for range pool.Results() {
		}
	}()

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		pool.Submit(Job{Index: i})
	}

	pool.Close()
}
