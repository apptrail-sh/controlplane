package sh.apptrail.controlplane.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/teams")
class TeamController(
) {
    @GetMapping
    fun listTeams() = emptyList<String>()
}
