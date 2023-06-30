package org.brandon.lanthrip.rewardpointsservice.model

abstract class Error(val message: String) {
    data class ParseError(val parseMessage: String): Error(parseMessage)
    data class MongoError(val mongoMessage: String): Error(mongoMessage)
}