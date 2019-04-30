@file:JvmName("DappMain")

package jp.co.soramitsu.dapp

import jp.co.soramitsu.dapp.service.DappService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan

@ComponentScan("jp.co.soramitsu.dapp")
class DappApplication

fun main(args: Array<String>) {
    val context = AnnotationConfigApplicationContext()
    GlobalScope.launch {
        context.getBean(DappService::class.java).init()
    }
}
