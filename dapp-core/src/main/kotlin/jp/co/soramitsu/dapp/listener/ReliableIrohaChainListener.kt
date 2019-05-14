/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.co.soramitsu.dapp.listener

import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Delivery
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import iroha.protocol.BlockOuterClass
import mu.KLogging
import java.io.Closeable
import java.util.concurrent.ExecutorService

private const val DEFAULT_LAST_READ_BLOCK = -1L

/**
 * Rabbit MQ implementation of Iroha chain listener
 * @param rmqHost - rmq host name
 * @param rmqExchange - rmq exchange name
 * @param irohaQueue - name of queue to read Iroha blocks from
 * @param consumerExecutorService - executor that is used to execure RabbitMQ consumer code.
 */
class ReliableIrohaChainListener(
    private val rmqHost: String,
    private val rmqPort: Int,
    rmqExchange: String,
    private val irohaQueue: String,
    private val consumerExecutorService: ExecutorService
) : Closeable {

    private val factory = ConnectionFactory()

    // Last read Iroha block number. Used to detect double read.
    private var lastReadBlockNum: Long = DEFAULT_LAST_READ_BLOCK

    private val conn by lazy {
        factory.host = rmqHost
        factory.port = rmqPort
        factory.newConnection(consumerExecutorService)
    }

    private val channel by lazy { conn.createChannel() }

    private var consumerTag: String? = null

    private val observable: Observable<BlockOuterClass.Block>

    init {
        channel.exchangeDeclare(rmqExchange, BuiltinExchangeType.FANOUT, true)
        channel.queueDeclare(irohaQueue, true, false, false, null)
        channel.queueBind(irohaQueue, rmqExchange, "")
        channel.basicQos(1)

        val source = PublishSubject.create<BlockOuterClass.Block>()
        val deliverCallback = { _: String, delivery: Delivery ->
            // This code is executed inside consumerExecutorService
            val block = iroha.protocol.BlockOuterClass.Block.parseFrom(delivery.body)
            //TODO shall we ignore too old blocks?
            if (ableToHandleBlock(block)) {
                source.onNext(block)
            } else {
                logger.warn { "Not able to handle Iroha block ${block.blockV1.payload.height}" }
            }
        }
        observable = source.map { block ->
            logger.info { "New Iroha block from RMQ arrived. Height ${block.blockV1.payload.height}" }
            block
        }
        consumerTag = channel.basicConsume(irohaQueue, true, deliverCallback, { _ -> })
    }

    /**
     * Returns an observable that emits a new block every time it gets it from Iroha
     */
    fun getBlockObservable(
    ): Observable<BlockOuterClass.Block> {
        return observable
    }

    /**
     * Checks if we are able to handle Iroha block
     * @param block - Iroha block to check
     * @return true if able
     */
    @Synchronized
    private fun ableToHandleBlock(block: BlockOuterClass.Block): Boolean {
        val height = block.blockV1.payload.height
        if (lastReadBlockNum == DEFAULT_LAST_READ_BLOCK) {
            //This is the very first block
            lastReadBlockNum = height
            return true
        } else if (height <= lastReadBlockNum) {
            logger.warn("Iroha block $height has been read previously")
            return false
        }
        val missedBlocks = height - lastReadBlockNum
        if (missedBlocks > 1) {
            logger.warn("Missed Iroha blocks $missedBlocks")
        }
        lastReadBlockNum = height
        return true
    }

    override fun close() {
        consumerTag?.let {
            channel.basicCancel(it)
        }
        consumerExecutorService.shutdownNow()
    }

    /**
     * Logger
     */
    companion object : KLogging()
}
