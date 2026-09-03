package pl.edu.praktyki.web.dto

import groovy.transform.ToString

@ToString(includeNames = true)
class ProcessBatchRequest {
    // null/puste -> procesujemy ALL
    String operationType
}