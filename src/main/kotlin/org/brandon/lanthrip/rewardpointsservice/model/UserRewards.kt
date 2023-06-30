package org.brandon.lanthrip.rewardpointsservice.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import jakarta.persistence.Id
import org.brandon.lanthrip.rewardpointsservice.service.RewardService.Companion.calculateReward
import org.brandon.lanthrip.rewardpointsservice.service.RewardService.Companion.createMongoId
import org.springframework.data.mongodb.core.mapping.Document
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Document(collection = "userRewards")
data class UserRewards(
    @Id
    val id: String,
    val userId: String,
    val month: Int,
    val year: Int,
    val rewards: List<Reward>
) {
    companion object {
        /**
         * This could be controversial. The models having specific logic is not super awesome, but I feel like
         * 'fromCsvLine' is totally fine because it's just a helper function to convert. Here I just imported the
         * bits of logic for the specific implementation from the service class, but likely I would refactor this to be
         * cleaner given more time
         */
        fun fromCsvLine(line: String): Either<Error, UserRewards> {
            val rewardTuple = line.split(",")
            return if (rewardTuple.size != 3) {
                Error.ParseError("Unable to parse line $line").left()
            } else {
                val userId = rewardTuple[0]
                val timeStamp = rewardTuple[1]

                when (val rewardPointAmount = Either.catch { calculateReward(rewardTuple[2].toDouble()) }) {
                    is Either.Left -> Error.ParseError("Unable to parse purchase amount from line $line").left()

                    is Either.Right -> {
                        when (val dateTime = Either.catch { OffsetDateTime.parse(timeStamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }) {
                            is Either.Left -> Error.ParseError("Unable to parse ISO Timestamp from Line $line").left()

                            is Either.Right -> {
                                val dateTimeValue = dateTime.value
                                val id = createMongoId(userId, dateTimeValue.monthValue, dateTimeValue.year)

                                UserRewards(
                                    id,
                                    userId,
                                    dateTimeValue.monthValue,
                                    dateTimeValue.year,
                                    listOf(Reward(timeStamp, rewardPointAmount.value))
                                ).right()
                            }
                        }
                    }
                }
            }
        }
    }
}
