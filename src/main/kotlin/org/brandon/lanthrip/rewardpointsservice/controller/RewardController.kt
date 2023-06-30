package org.brandon.lanthrip.rewardpointsservice.controller

import org.brandon.lanthrip.rewardpointsservice.service.RewardService
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@RestController
@RequestMapping("/rewards")
class RewardController(private val rewardService: RewardService) {

    /**
     * Will take a multiPart file (up to 1GB) and async process it.
     * The user will be returned a processId, which is a UUID that is generated
     * and will be used to publish updates on the corresponding WS topic
     */
    @PostMapping("/batch")
    fun batchRewards(@RequestParam("file") f: MultipartFile): Map<String, UUID> {
        val processId = UUID.randomUUID()
        rewardService.processFilePreProcess(f, processId)
        return mapOf("processId" to processId)
    }

    /**
     * Fetch a single rewards for a specific month and year for a user
     */
    @GetMapping("/{userId}")
    fun getRewards(@PathVariable userId: String, @RequestParam("monthYear") monthYear: String): Int =
        rewardService.getUserRewardsForMonthYear(userId, monthYear)

    /**
     * Fetch a list of rewards by providing an inclusive range of months and a given year.
     * @todo - Support multi-year requests
     */
    @GetMapping("/range/{userId}")
    fun getRewardsForInclusiveRange(@PathVariable userId: String,
                                    @RequestParam("startMonth") startMonth: Int,
                                    @RequestParam("endMonth") endMonth: Int,
                                    @RequestParam("year") year: String): List<Map<String, Int>> {
        return if (startMonth in 0 until endMonth && endMonth <= 11) {
            (startMonth..endMonth).map {
                mapOf("$it$year" to rewardService.getUserRewardsForMonthYear(userId, "$it$year"))
            }
        } else throw Exception("$startMonth .. $endMonth is an invalid query")

    }
}