// Package buffers provides memory-efficient buffer pooling for high-throughput data processing.
// Uses multiple sync.Pool instances with size-graduated buffers to minimize allocations
// and GC pressure during import operations.
package buffers

import (
	"runtime"
	"sync"
	"sync/atomic"
	"time"
)

const (
	// BufferRotationInterval is the number of rows after which pools are rotated
	// to prevent memory fragmentation from accumulated oversized buffers.
	BufferRotationInterval = 100_000_000
)

var (
	rowsProcessedSinceRotation int64
	bufferRotationCount        int64
	lastRotationTime           time.Time
	rotationMu                 sync.Mutex
)

// Pool statistics for monitoring
var (
	decompressPoolGets  int64
	copyPoolGets        int64
	lineBufferPoolGets  int64
	rowBufferPoolGets   int64
	rowBufferDirectAllocs int64
)

// Multi-tier buffer pools for different operations
var (
	// DecompressBufferPool - Large buffers for file I/O and decompression (256KB)
	decompressBufferPool = sync.Pool{
		New: func() interface{} { return make([]byte, 256*1024) },
	}

	// CopyBufferPool - Medium buffers for PostgreSQL COPY streaming (64KB)
	copyBufferPool = sync.Pool{
		New: func() interface{} { return make([]byte, 64*1024) },
	}

	// LineBufferPool - Buffers for CSV line operations (4KB initial capacity)
	lineBufferPool = sync.Pool{
		New: func() interface{} { return make([]byte, 0, 4*1024) },
	}

	// ColumnPosPool - For zero-allocation CSV column position tracking
	columnPosPool = sync.Pool{
		New: func() interface{} { return make([]int, 64) },
	}

	// Size-graduated row buffers for varying row sizes
	rowPool64  = sync.Pool{New: func() interface{} { return make([]byte, 64) }}
	rowPool256 = sync.Pool{New: func() interface{} { return make([]byte, 256) }}
	rowPool1K  = sync.Pool{New: func() interface{} { return make([]byte, 1024) }}
	rowPool4K  = sync.Pool{New: func() interface{} { return make([]byte, 4096) }}
	rowPool16K = sync.Pool{New: func() interface{} { return make([]byte, 16384) }}
	rowPool64K = sync.Pool{New: func() interface{} { return make([]byte, 65536) }}
)

// GetDecompressBuffer returns a 256KB buffer for decompression operations.
func GetDecompressBuffer() []byte {
	atomic.AddInt64(&decompressPoolGets, 1)
	return decompressBufferPool.Get().([]byte)
}

// ReturnDecompressBuffer returns a decompression buffer to the pool.
func ReturnDecompressBuffer(buf []byte) {
	if cap(buf) >= 256*1024 {
		decompressBufferPool.Put(buf[:256*1024])
	}
}

// GetCopyBuffer returns a 64KB buffer for COPY operations.
func GetCopyBuffer() []byte {
	atomic.AddInt64(&copyPoolGets, 1)
	return copyBufferPool.Get().([]byte)
}

// ReturnCopyBuffer returns a COPY buffer to the pool.
func ReturnCopyBuffer(buf []byte) {
	if cap(buf) >= 64*1024 {
		copyBufferPool.Put(buf[:64*1024])
	}
}

// GetLineBuffer returns a buffer for CSV line operations.
func GetLineBuffer() []byte {
	atomic.AddInt64(&lineBufferPoolGets, 1)
	return lineBufferPool.Get().([]byte)
}

// ReturnLineBuffer returns a line buffer to the pool.
func ReturnLineBuffer(buf []byte) {
	if cap(buf) >= 4*1024 {
		lineBufferPool.Put(buf[:0])
	}
}

// GetColumnPositions returns a buffer for tracking CSV column positions.
func GetColumnPositions() []int {
	return columnPosPool.Get().([]int)
}

// ReturnColumnPositions returns a column positions buffer to the pool.
func ReturnColumnPositions(pos []int) {
	if cap(pos) >= 64 {
		columnPosPool.Put(pos[:0])
	}
}

// GetRowBuffer returns an optimally-sized buffer from the appropriate pool.
// The returned buffer has length=size but may have larger capacity.
func GetRowBuffer(size int) []byte {
	atomic.AddInt64(&rowBufferPoolGets, 1)
	switch {
	case size <= 64:
		return rowPool64.Get().([]byte)[:size]
	case size <= 256:
		return rowPool256.Get().([]byte)[:size]
	case size <= 1024:
		return rowPool1K.Get().([]byte)[:size]
	case size <= 4096:
		return rowPool4K.Get().([]byte)[:size]
	case size <= 16384:
		return rowPool16K.Get().([]byte)[:size]
	case size <= 65536:
		return rowPool64K.Get().([]byte)[:size]
	default:
		// Direct allocation for oversized buffers
		atomic.AddInt64(&rowBufferDirectAllocs, 1)
		return make([]byte, size)
	}
}

// ReturnRowBuffer returns a row buffer to the appropriate pool based on capacity.
func ReturnRowBuffer(buf []byte) {
	switch cap(buf) {
	case 64:
		rowPool64.Put(buf[:64])
	case 256:
		rowPool256.Put(buf[:256])
	case 1024:
		rowPool1K.Put(buf[:1024])
	case 4096:
		rowPool4K.Put(buf[:4096])
	case 16384:
		rowPool16K.Put(buf[:16384])
	case 65536:
		rowPool64K.Put(buf[:65536])
	}
}

// MaybeRotate checks if enough rows have been processed to trigger pool rotation.
// This prevents memory fragmentation from accumulated oversized buffers.
func MaybeRotate(rowCount int64) {
	if atomic.AddInt64(&rowsProcessedSinceRotation, rowCount) >= BufferRotationInterval {
		RotatePools()
	}
}

// RotatePools recreates all pools to release accumulated oversized buffers.
// This should be called periodically during long-running imports.
func RotatePools() {
	rotationMu.Lock()
	defer rotationMu.Unlock()

	// Double-check after acquiring lock
	if atomic.LoadInt64(&rowsProcessedSinceRotation) < BufferRotationInterval {
		return
	}

	// Force GC before rotation
	runtime.GC()

	// Recreate all pools
	decompressBufferPool = sync.Pool{New: func() interface{} { return make([]byte, 256*1024) }}
	copyBufferPool = sync.Pool{New: func() interface{} { return make([]byte, 64*1024) }}
	lineBufferPool = sync.Pool{New: func() interface{} { return make([]byte, 0, 4*1024) }}
	columnPosPool = sync.Pool{New: func() interface{} { return make([]int, 64) }}
	rowPool64 = sync.Pool{New: func() interface{} { return make([]byte, 64) }}
	rowPool256 = sync.Pool{New: func() interface{} { return make([]byte, 256) }}
	rowPool1K = sync.Pool{New: func() interface{} { return make([]byte, 1024) }}
	rowPool4K = sync.Pool{New: func() interface{} { return make([]byte, 4096) }}
	rowPool16K = sync.Pool{New: func() interface{} { return make([]byte, 16384) }}
	rowPool64K = sync.Pool{New: func() interface{} { return make([]byte, 65536) }}

	// Force GC after rotation
	runtime.GC()

	atomic.StoreInt64(&rowsProcessedSinceRotation, 0)
	atomic.AddInt64(&bufferRotationCount, 1)
	lastRotationTime = time.Now()
}

// Stats returns current buffer pool statistics.
type Stats struct {
	DecompressGets     int64
	CopyGets           int64
	LineBufferGets     int64
	RowBufferGets      int64
	RowBufferDirect    int64
	RowsSinceRotation  int64
	RotationCount      int64
	LastRotation       time.Time
}

// GetStats returns current pool statistics for monitoring.
func GetStats() Stats {
	return Stats{
		DecompressGets:    atomic.LoadInt64(&decompressPoolGets),
		CopyGets:          atomic.LoadInt64(&copyPoolGets),
		LineBufferGets:    atomic.LoadInt64(&lineBufferPoolGets),
		RowBufferGets:     atomic.LoadInt64(&rowBufferPoolGets),
		RowBufferDirect:   atomic.LoadInt64(&rowBufferDirectAllocs),
		RowsSinceRotation: atomic.LoadInt64(&rowsProcessedSinceRotation),
		RotationCount:     atomic.LoadInt64(&bufferRotationCount),
		LastRotation:      lastRotationTime,
	}
}

// ResetStats resets all pool statistics (useful for testing).
func ResetStats() {
	atomic.StoreInt64(&decompressPoolGets, 0)
	atomic.StoreInt64(&copyPoolGets, 0)
	atomic.StoreInt64(&lineBufferPoolGets, 0)
	atomic.StoreInt64(&rowBufferPoolGets, 0)
	atomic.StoreInt64(&rowBufferDirectAllocs, 0)
	atomic.StoreInt64(&rowsProcessedSinceRotation, 0)
	atomic.StoreInt64(&bufferRotationCount, 0)
}
