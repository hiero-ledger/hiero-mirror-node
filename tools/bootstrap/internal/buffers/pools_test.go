package buffers

import (
	"sync"
	"testing"
)

func TestGetDecompressBuffer(t *testing.T) {
	ResetStats()

	buf := GetDecompressBuffer()
	if len(buf) != 256*1024 {
		t.Errorf("Expected buffer length 256KB, got %d", len(buf))
	}
	if cap(buf) < 256*1024 {
		t.Errorf("Expected buffer capacity >= 256KB, got %d", cap(buf))
	}

	stats := GetStats()
	if stats.DecompressGets != 1 {
		t.Errorf("Expected 1 decompress get, got %d", stats.DecompressGets)
	}

	ReturnDecompressBuffer(buf)
}

func TestGetCopyBuffer(t *testing.T) {
	ResetStats()

	buf := GetCopyBuffer()
	if len(buf) != 64*1024 {
		t.Errorf("Expected buffer length 64KB, got %d", len(buf))
	}

	stats := GetStats()
	if stats.CopyGets != 1 {
		t.Errorf("Expected 1 copy get, got %d", stats.CopyGets)
	}

	ReturnCopyBuffer(buf)
}

func TestGetRowBuffer_SizeSelection(t *testing.T) {
	ResetStats()

	tests := []struct {
		size        int
		expectedCap int
	}{
		{32, 64},
		{64, 64},
		{100, 256},
		{256, 256},
		{500, 1024},
		{1024, 1024},
		{2000, 4096},
		{4096, 4096},
		{8000, 16384},
		{16384, 16384},
		{32000, 65536},
		{65536, 65536},
	}

	for _, tc := range tests {
		buf := GetRowBuffer(tc.size)
		if len(buf) != tc.size {
			t.Errorf("GetRowBuffer(%d): expected len=%d, got %d", tc.size, tc.size, len(buf))
		}
		if cap(buf) != tc.expectedCap {
			t.Errorf("GetRowBuffer(%d): expected cap=%d, got %d", tc.size, tc.expectedCap, cap(buf))
		}
		ReturnRowBuffer(buf)
	}
}

func TestGetRowBuffer_Oversized(t *testing.T) {
	ResetStats()

	// Request buffer larger than largest pool
	size := 100000
	buf := GetRowBuffer(size)
	if len(buf) != size {
		t.Errorf("Expected len=%d, got %d", size, len(buf))
	}

	stats := GetStats()
	if stats.RowBufferDirect != 1 {
		t.Errorf("Expected 1 direct allocation, got %d", stats.RowBufferDirect)
	}

	// Return oversized buffer (should be ignored, not panic)
	ReturnRowBuffer(buf)
}

func TestBufferPoolReuse(t *testing.T) {
	ResetStats()

	// Get and return same buffer multiple times
	for i := 0; i < 10; i++ {
		buf := GetDecompressBuffer()
		ReturnDecompressBuffer(buf)
	}

	// Due to sync.Pool behavior, we should have 10 gets
	stats := GetStats()
	if stats.DecompressGets != 10 {
		t.Errorf("Expected 10 decompress gets, got %d", stats.DecompressGets)
	}
}

func TestGetLineBuffer(t *testing.T) {
	ResetStats()

	buf := GetLineBuffer()
	if len(buf) != 0 {
		t.Errorf("Expected empty slice, got len=%d", len(buf))
	}
	if cap(buf) < 4*1024 {
		t.Errorf("Expected capacity >= 4KB, got %d", cap(buf))
	}

	stats := GetStats()
	if stats.LineBufferGets != 1 {
		t.Errorf("Expected 1 line buffer get, got %d", stats.LineBufferGets)
	}

	ReturnLineBuffer(buf)
}

func TestGetColumnPositions(t *testing.T) {
	pos := GetColumnPositions()
	if cap(pos) < 64 {
		t.Errorf("Expected capacity >= 64, got %d", cap(pos))
	}
	ReturnColumnPositions(pos)
}

func TestConcurrentAccess(t *testing.T) {
	ResetStats()

	var wg sync.WaitGroup
	iterations := 1000
	goroutines := 10

	for g := 0; g < goroutines; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := 0; i < iterations; i++ {
				buf := GetRowBuffer(100)
				copy(buf, []byte("test data"))
				ReturnRowBuffer(buf)

				dbuf := GetDecompressBuffer()
				ReturnDecompressBuffer(dbuf)
			}
		}()
	}

	wg.Wait()

	stats := GetStats()
	expectedGets := int64(goroutines * iterations)
	if stats.RowBufferGets != expectedGets {
		t.Errorf("Expected %d row buffer gets, got %d", expectedGets, stats.RowBufferGets)
	}
	if stats.DecompressGets != expectedGets {
		t.Errorf("Expected %d decompress gets, got %d", expectedGets, stats.DecompressGets)
	}
}

func TestMaybeRotate(t *testing.T) {
	ResetStats()

	// Should not rotate with small row count
	MaybeRotate(1000)
	stats := GetStats()
	if stats.RotationCount != 0 {
		t.Errorf("Should not have rotated yet, got count=%d", stats.RotationCount)
	}
	if stats.RowsSinceRotation != 1000 {
		t.Errorf("Expected 1000 rows since rotation, got %d", stats.RowsSinceRotation)
	}
}

func TestRotatePools(t *testing.T) {
	ResetStats()

	// Force rotation
	RotatePools()

	// RotatePools checks threshold, so count may not increment
	// unless threshold is met. Just verify no panic occurs.
}

func TestStats_Struct(t *testing.T) {
	ResetStats()

	// Get some buffers to populate stats
	buf1 := GetDecompressBuffer()
	ReturnDecompressBuffer(buf1)

	buf2 := GetCopyBuffer()
	ReturnCopyBuffer(buf2)

	buf3 := GetLineBuffer()
	ReturnLineBuffer(buf3)

	buf4 := GetRowBuffer(100)
	ReturnRowBuffer(buf4)

	stats := GetStats()

	if stats.DecompressGets != 1 {
		t.Errorf("DecompressGets: expected 1, got %d", stats.DecompressGets)
	}
	if stats.CopyGets != 1 {
		t.Errorf("CopyGets: expected 1, got %d", stats.CopyGets)
	}
	if stats.LineBufferGets != 1 {
		t.Errorf("LineBufferGets: expected 1, got %d", stats.LineBufferGets)
	}
	if stats.RowBufferGets != 1 {
		t.Errorf("RowBufferGets: expected 1, got %d", stats.RowBufferGets)
	}
}

func TestReturnDecompressBuffer_Undersized(t *testing.T) {
	// Returning an undersized buffer should not panic
	smallBuf := make([]byte, 100)
	ReturnDecompressBuffer(smallBuf) // Should be silently ignored
}

func TestReturnCopyBuffer_Undersized(t *testing.T) {
	smallBuf := make([]byte, 100)
	ReturnCopyBuffer(smallBuf) // Should be silently ignored
}

func TestReturnLineBuffer_Undersized(t *testing.T) {
	smallBuf := make([]byte, 100)
	ReturnLineBuffer(smallBuf) // Should be silently ignored
}

func TestReturnColumnPositions_Undersized(t *testing.T) {
	smallPos := make([]int, 10)
	ReturnColumnPositions(smallPos) // Should be silently ignored
}

func TestReturnRowBuffer_AllSizes(t *testing.T) {
	sizes := []int{64, 256, 1024, 4096, 16384, 65536}
	for _, size := range sizes {
		buf := make([]byte, size)
		ReturnRowBuffer(buf) // Should not panic
	}

	// Also test odd-sized buffers that don't match pool sizes
	oddBuf := make([]byte, 500)
	ReturnRowBuffer(oddBuf) // Should be silently ignored
}

func TestBufferRotationInterval(t *testing.T) {
	if BufferRotationInterval != 100_000_000 {
		t.Errorf("BufferRotationInterval: expected 100_000_000, got %d", BufferRotationInterval)
	}
}

func TestGetRowBuffer_EdgeCases(t *testing.T) {
	ResetStats()

	// Zero size
	buf := GetRowBuffer(0)
	if len(buf) != 0 {
		t.Errorf("Expected len=0 for size 0, got %d", len(buf))
	}

	// Exactly 1 byte
	buf = GetRowBuffer(1)
	if len(buf) != 1 {
		t.Errorf("Expected len=1, got %d", len(buf))
	}
	ReturnRowBuffer(buf)
}

func BenchmarkGetRowBuffer64(b *testing.B) {
	for i := 0; i < b.N; i++ {
		buf := GetRowBuffer(64)
		ReturnRowBuffer(buf)
	}
}

func BenchmarkGetRowBuffer1K(b *testing.B) {
	for i := 0; i < b.N; i++ {
		buf := GetRowBuffer(1024)
		ReturnRowBuffer(buf)
	}
}

func BenchmarkGetDecompressBuffer(b *testing.B) {
	for i := 0; i < b.N; i++ {
		buf := GetDecompressBuffer()
		ReturnDecompressBuffer(buf)
	}
}

func BenchmarkGetCopyBuffer(b *testing.B) {
	for i := 0; i < b.N; i++ {
		buf := GetCopyBuffer()
		ReturnCopyBuffer(buf)
	}
}

func BenchmarkGetLineBuffer(b *testing.B) {
	for i := 0; i < b.N; i++ {
		buf := GetLineBuffer()
		ReturnLineBuffer(buf)
	}
}

func BenchmarkDirectAllocation64(b *testing.B) {
	for i := 0; i < b.N; i++ {
		buf := make([]byte, 64)
		_ = buf
	}
}

func BenchmarkDirectAllocation1K(b *testing.B) {
	for i := 0; i < b.N; i++ {
		buf := make([]byte, 1024)
		_ = buf
	}
}

