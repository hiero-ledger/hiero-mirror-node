// Package util provides common utility functions.
package util

import (
	"fmt"
	"time"

	"golang.org/x/text/language"
	"golang.org/x/text/message"
)

var printer = message.NewPrinter(language.English)

// FormatNumber formats an integer with thousands separators.
func FormatNumber(n int64) string {
	return printer.Sprintf("%d", n)
}

// FormatBytes formats bytes as human-readable string.
func FormatBytes(b int64) string {
	const unit = 1024
	if b < unit {
		return fmt.Sprintf("%d B", b)
	}
	div, exp := int64(unit), 0
	for n := b / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(b)/float64(div), "KMGTPE"[exp])
}

// FormatDuration formats a duration as human-readable string.
func FormatDuration(d time.Duration) string {
	if d < time.Minute {
		return fmt.Sprintf("%.1fs", d.Seconds())
	}
	if d < time.Hour {
		return fmt.Sprintf("%dm%ds", int(d.Minutes()), int(d.Seconds())%60)
	}
	return fmt.Sprintf("%dh%dm%ds", int(d.Hours()), int(d.Minutes())%60, int(d.Seconds())%60)
}

// FormatRate formats a rate as "N/s" with thousands separators.
func FormatRate(rate float64) string {
	return printer.Sprintf("%.0f/s", rate)
}

// FormatPercentage formats a percentage with 2 decimal places.
func FormatPercentage(pct float64) string {
	return fmt.Sprintf("%.2f%%", pct)
}
