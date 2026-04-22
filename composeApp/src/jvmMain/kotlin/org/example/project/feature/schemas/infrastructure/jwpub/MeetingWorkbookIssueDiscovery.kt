package org.example.project.feature.schemas.infrastructure.jwpub

object MeetingWorkbookIssueDiscovery {
    private val BIMESTER_MONTHS = listOf(1, 3, 5, 7, 9, 11)

    fun candidatesForYear(year: Int, startingFromMonth: Int): List<String> {
        require(year in 1000..9999) { "year out of range: $year" }
        require(startingFromMonth in 1..12) { "month out of range: $startingFromMonth" }
        return BIMESTER_MONTHS
            .filter { issueMonth -> issueMonth + 1 >= startingFromMonth }
            .map { month -> "%04d%02d".format(year, month) }
    }
}
