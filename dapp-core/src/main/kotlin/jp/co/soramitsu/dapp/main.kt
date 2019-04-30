@file:JvmName("DappMain")

package jp.co.soramitsu.dapp

import jp.co.soramitsu.dapp.service.DappService
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan

@ComponentScan("jp.co.soramitsu.dapp")
class DappApplication

fun main(args: Array<String>) {
    val context = AnnotationConfigApplicationContext()
    context.register(DappApplication::class.java)
    context.refresh()
    context.getBean(DappService::class.java).init()
}
