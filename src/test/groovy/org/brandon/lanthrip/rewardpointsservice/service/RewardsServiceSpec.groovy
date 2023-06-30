package org.brandon.lanthrip.rewardpointsservice.service

import org.brandon.lanthrip.rewardpointsservice.repository.UserRewardsRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification
import spock.lang.Unroll
import spock.mock.DetachedMockFactory

/**
 * I started to write some tests, but to be honest by this point I feel like I have shown enough. If
 * you haven't seen Spock before it's probably the best testing framework I have ever used!
 */
class RewardsServiceSpec extends Specification {

    def mockFactory = new DetachedMockFactory()

    def userRewardsRepository = Mock(UserRewardsRepository)
    def simpMessagingTemplate = Mock(SimpMessagingTemplate)
    def mongoTemplate = Mock(MongoTemplate)

    @Unroll
    def "test reward calculation logic"() {
        when: "the conversion function is called"
        int reward = RewardService.@Companion.calculateReward(purchase)

        then: "the reward matches the expected amount"
        reward == expected

        where:
        purchase | expected
        120.00   | 90
        55.00    | 5
        25.00    | 0
        252.00   | 354
    }

    def "getUserRewardsForMonthYear returns expected rewards"() {
        given: "An instance of the service"
        def rewardService = new RewardService(userRewardsRepository, simpMessagingTemplate, mongoTemplate)

        and: "Expected rewards for user and monthYear"
        def userId = "user1"
        def monthYear = "42023"
        def expectedRewards = 100
        userRewardsRepository.getTotalRewardsForMonth(_ as String) >> expectedRewards

        when: "getUserRewardsForMonthYear is called"
        def rewards = rewardService.getUserRewardsForMonthYear(userId, monthYear)

        then: "Returned rewards are as expected"
        rewards == expectedRewards
    }

    def "processFile throws exception when parsing line fails"() {
        given: "An instance of the service"
        def rewardService = new RewardService(userRewardsRepository, simpMessagingTemplate, mongoTemplate)

        and: "A file with invalid line"
        def file = mockFactory.Mock(MultipartFile)
        def inputStream = new ByteArrayInputStream("invalid line".getBytes())
        file.getInputStream() >> inputStream

        and: "A processId"
        def processId = UUID.randomUUID()

        when: "processFile is called"
        rewardService.processFile(file, processId)

        then: "Exception is thrown"
        thrown(Exception)
    }
}