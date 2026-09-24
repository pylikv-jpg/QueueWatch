package com.pylikv.queuewatch.forecast

// Public client credential only. No direct table privileges; JWT-verified ingestion.
internal object ForecastEndpoint {
    const val URL = "https://whvdyxjopfwgzgqqvzaj.supabase.co/functions/v1/submit-queuewatch-forecast"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndodmR5eGpvcGZ3Z3pncXF2emFqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAyNDU3MzksImV4cCI6MjEwNTgyMTczOX0.LaWuG5qKM5e5H_6yRjcQHW7ZH6kgWGU7VLHDCVv-l8g"
}
