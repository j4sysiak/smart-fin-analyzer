package pl.edu.praktyki.web.dto

import groovy.transform.ToString

import java.time.OffsetDateTime

@ToString(includeNames = true)
class ProcessBatchResponse {
    String trigger
    int total
    int saved
    int skipped
    int failed
    OffsetDateTime processedAt
}