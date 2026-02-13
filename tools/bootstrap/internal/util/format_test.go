package util

import (
	"testing"
	"time"
)

func TestFormatNumber(t *testing.T) {
	tests := []struct {
		n        int64
		expected string
	}{
		{0, "0"},
		{999, "999"},
		{1000, "1,000"},
		{1234567, "1,234,567"},
		{1000000000, "1,000,000,000"},
	}

	for _, tc := range tests {
		result := FormatNumber(tc.n)
		if result != tc.expected {
			t.Errorf("FormatNumber(%d): expected %q, got %q", tc.n, tc.expected, result)
		}
	}
}

func TestFormatBytes(t *testing.T) {
	tests := []struct {
		b        int64
		expected string
	}{
		{0, "0 B"},
		{500, "500 B"},
		{1024, "1.0 KB"},
		{1536, "1.5 KB"},
		{1048576, "1.0 MB"},
		{1073741824, "1.0 GB"},
	}

	for _, tc := range tests {
		result := FormatBytes(tc.b)
		if result != tc.expected {
			t.Errorf("FormatBytes(%d): expected %q, got %q", tc.b, tc.expected, result)
		}
	}
}

func TestFormatDuration(t *testing.T) {
	tests := []struct {
		d        time.Duration
		expected string
	}{
		{5 * time.Second, "5.0s"},
		{30 * time.Second, "30.0s"},
		{90 * time.Second, "1m30s"},
		{3661 * time.Second, "1h1m1s"},
	}

	for _, tc := range tests {
		result := FormatDuration(tc.d)
		if result != tc.expected {
			t.Errorf("FormatDuration(%v): expected %q, got %q", tc.d, tc.expected, result)
		}
	}
}

func TestFormatPercentage(t *testing.T) {
	tests := []struct {
		pct      float64
		expected string
	}{
		{0, "0.00%"},
		{50.5, "50.50%"},
		{100, "100.00%"},
	}

	for _, tc := range tests {
		result := FormatPercentage(tc.pct)
		if result != tc.expected {
			t.Errorf("FormatPercentage(%f): expected %q, got %q", tc.pct, tc.expected, result)
		}
	}
}

func BenchmarkFormatNumber(b *testing.B) {
	for i := 0; i < b.N; i++ {
		FormatNumber(1234567890)
	}
}

func BenchmarkFormatBytes(b *testing.B) {
	for i := 0; i < b.N; i++ {
		FormatBytes(1073741824)
	}
}
