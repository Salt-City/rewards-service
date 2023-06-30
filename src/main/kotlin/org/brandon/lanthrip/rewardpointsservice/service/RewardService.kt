package org.brandon.lanthrip.rewardpointsservice.service

import arrow.core.Either
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.brandon.lanthrip.rewardpointsservice.model.Reward
import org.brandon.lanthrip.rewardpointsservice.model.UserRewards
import org.brandon.lanthrip.rewardpointsservice.repository.UserRewardsRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

private val logger = KotlinLogging.logger {}
@Service
class RewardService (
    private val userRewardsRepository: UserRewardsRepository,
    private val simpMessagingTemplate: SimpMessagingTemplate,
    val mongoTemplate: MongoTemplate
) {

    companion object {
        fun calculateReward(purchase: Double): Int {
            return if (purchase < 0.0) 0
            else when (val p = purchase.toInt()) {
                in 0..50 -> 0
                in 51..100 -> p - 50
                else -> 50 + ((p - 100) * 2)
            }
        }

        fun createMongoId(userId: String, month: Int, year: Int) = createMongoId(userId, "$month$year")
        fun createMongoId(userId: String, monthYear: String) = "${userId}_$monthYear"
    }

    /**
     * @todo - This is a batch process, so really that should mean all or nothing. I handled the errors here,
     * but I would also need to rollback any changes made in the case where I might lose connection to the Mongo
     * half way through a process. The later in-memory pre-process function does handle this a little better
     * because it is submitting the entire change for a user document in one go, so in the event of an error where
     * the file is aborted halfway through we could just change the logic to overwrite a document if it already exists
     * so we can run the file over again (in this case we are assuming here that the file contains a total month and
     * not a partial amount for the month)
     */

    /**
     * In here I have three different methods because I wanted to test speed differences for writing
     * large csv files to mongo. This is the most plain-jane one. I simply take the file and write line by
     * line into mongo. This is pretty far from performant, and I found that loading a file almost 200MB into mongo
     * took a solid amount of time (several minutes)
     */
    @Async
    fun processFile(file: MultipartFile, processId: UUID) {
        val userMonths = mutableSetOf<String>()

        file.inputStream.bufferedReader().forEachLine {
            when (val userRewards = UserRewards.fromCsvLine(it)) {
                is Either.Left -> {
                    publishMessage("ERROR: ${userRewards.value.message}", processId)
                    logger.error { "There was an error parsing line: $it" }
                    throw Exception(userRewards.value.message)
                }
                is Either.Right -> {
                    val userRewardsValue = userRewards.value
                    if (userMonths.contains(userRewardsValue.id)) {
                        Either.catch { updateMongo(userRewardsValue, ::updateOne) }
                    } else {
                        if (userRewardsRepository.existsById(userRewardsValue.id)) {
                            Either.catch { updateMongo(userRewardsValue, ::updateOne) }
                        } else {
                            Either.catch { userRewardsRepository.save(userRewardsValue) }
                        }
                    }.fold(
                        ifLeft = { throwable ->
                            logger.error { "There was an error connecting to Mongo: ${throwable.message}" }
                            publishMessage(throwable.message ?: "No message provided", processId)
                            throw throwable
                        },
                        ifRight = {_ -> userMonths.add(userRewardsValue.id) }
                    )

                }
            }
        }

    }

    /**
     * For this function I did basically the same thing except I wanted to see if using coroutines would
     * improve the performance while still streaming the file. This did not seem to improve much, and I suspect it
     * is because of the way Mongo locks documents
     */
    @Async
    fun processFileCoroutine(file: MultipartFile, processId: UUID) = runBlocking {
        val userMonths = mutableSetOf<String>()
        val channel = Channel<String>()
        val producer = launch {
            file.inputStream.bufferedReader().useLines { lines ->
                for (l in lines) { channel.send(l) }
            }
        }

        val consumers = List(4) {
            launch {
                for (line in channel) {
                    when (val userRewards = UserRewards.fromCsvLine(line)) {
                        is Either.Left -> {
                            publishMessage("ERROR: ${userRewards.value.message}", processId)
                            logger.error { "There was an error parsing line: $it" }
                            throw Exception(userRewards.value.message)
                        }
                        is Either.Right -> {
                            val userRewardsValue = userRewards.value
                            if (userMonths.contains(userRewardsValue.id)) {
                                Either.catch { updateMongo(userRewardsValue, ::updateOne) }
                            } else {
                                if (userRewardsRepository.existsById(userRewardsValue.id)) {
                                    Either.catch { updateMongo(userRewardsValue, ::updateOne) }
                                } else {
                                    Either.catch { userRewardsRepository.save(userRewardsValue) }
                                }
                            }.fold(
                                ifLeft = { throwable ->
                                    logger.error { "There was an error connecting to Mongo: ${throwable.message}" }
                                    publishMessage(throwable.message ?: "No message provided", processId)
                                    throw throwable
                                },
                                ifRight = {_ -> userMonths.add(userRewardsValue.id) }
                            )

                        }
                    }
                }
            }
        }
        producer.join()
        consumers.forEach{ it.join() }
    }

    /**
     * This is the most memory intensive, but I can load over 6k documents with ~300 rewards each into a db
     * in < 1 minute. I could most likely further improve this by using coroutines, since now we have pre-processed and
     * grouped the records by document
     */
    @Async
    fun processFilePreProcess(file: MultipartFile, processId: UUID) {
        val userMonths = mutableSetOf<String>()

        file.inputStream.bufferedReader().lines()
            .map { line ->
                UserRewards.fromCsvLine(line).fold(
                ifLeft = {
                    publishMessage("ERROR: ${it.message}", processId)
                    throw Exception("There was an error parsing the csv: ${it.message}") },
                ifRight = { it }
            ) }
            .toList()
            .groupBy { it.id }
            .map {
                val first = it.value.first()
                UserRewards(
                    id = it.key,
                    userId = first.userId,
                    month = first.month,
                    year = first.year,
                    rewards = it.value.flatMap {v -> v.rewards }
                )
            }
            .forEach { userReward ->
                if (userMonths.contains(userReward.id)) {
                    Either.catch { updateMongo(userReward, ::updateAll) }
                } else {
                    if (userRewardsRepository.existsById(userReward.id)) {
                        Either.catch { updateMongo(userReward, ::updateAll) }
                    } else {
                        Either.catch { userRewardsRepository.save(userReward) }
                    }
                }.fold(
                    ifLeft = { throwable ->
                        logger.error { "There was an error connecting to Mongo: ${throwable.message}" }
                        publishMessage(throwable.message ?: "No message provided", processId)
                        throw throwable
                    },
                    ifRight = {_ -> userMonths.add(userReward.id) }
                )
            }
        publishMessage("success", processId)
    }

    /**
     * This will use an aggregation query to sum the rewards for a month up
     */
    fun getUserRewardsForMonthYear(userId: String, monthYear: String): Int =
        userRewardsRepository.getTotalRewardsForMonth(createMongoId(userId, monthYear))

    /**
     * publish to a websocket on a specific topic
     */
    private fun publishMessage(message: String, id: UUID) =
        simpMessagingTemplate.convertAndSend("/batch/$id", message)

    /**
     * Update mongo, using a little bit of higher order functions here which is always fun
     */
    private fun updateMongo(userRewards: UserRewards, u: (List<Reward>) -> Update)  {
        val q = Query(Criteria.where("_id").`is`(userRewards.id))
        if (!mongoTemplate.updateFirst(q, u(userRewards.rewards), UserRewards::class.java).wasAcknowledged()) {
            throw Exception("Update was unacknowledged by mongo: UserId: ${userRewards.userId} Rewards: ${userRewards.rewards.last()}")
        }
    }

    private fun updateOne(rewards: List<Reward>): Update = Update().push("rewards", rewards.last())

    private fun updateAll(rewards: List<Reward>): Update = Update().push("rewards", rewards)

}