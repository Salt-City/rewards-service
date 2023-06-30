package org.brandon.lanthrip.rewardpointsservice.repository

import org.brandon.lanthrip.rewardpointsservice.model.Reward
import org.brandon.lanthrip.rewardpointsservice.model.UserRewards
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserRewardsRepository: MongoRepository<UserRewards, String> {

    @Aggregation(pipeline = [
        "{ '\$match' :  { '_id':  ?0 } }",
        "{ '\$unwind':  '\$rewards' }",
        "{ '\$group' : { '_id' :  null, 'total' :  { '\$sum' :  '\$rewards.rewardPointAmt' } } }"
    ])
    fun getTotalRewardsForMonth(id: String): Int
}