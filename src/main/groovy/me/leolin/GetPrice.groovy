package me.leolin

import groovy.json.JsonSlurper
import groovy.sql.Sql
import org.jsoup.Jsoup
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rx.Observable
import rx.schedulers.Schedulers
import rx.util.async.Async

import java.util.stream.Collectors

/**
 * @author Leolin
 */
@RestController
class GetPrice {
    static sql = Sql.newInstance(
            'jdbc:mysql://10.0.1.50/Stock?useUnicode=true&characterEncoding=UTF-8',
            'root',
            'ro@t',
            'com.mysql.jdbc.Driver'
    )

    static def getCookies() {
        Jsoup.connect("http://mis.twse.com.tw/stock/fibest.jsp")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36")
                .header("Connection", "Keep-alive")
                .header("Host", "mis.twse.com.tw")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Encoding:gzip", "deflate, sdch")
                .header("Accept-Language", "zh-TW,zh;q=0.8,en-US;q=0.6,en;q=0.4,zh-CN;q=0.2,de;q=0.2")
                .execute()
                .cookies()
    }


    static def getQueryStrings() {
        def queryStrings = []
        def list = []

        def rows = sql.rows("Select * from stock")
        Collections.shuffle(rows)
        rows.forEach {
            list.add("${it.market}_${it.ch}")
            if (list.size() == 80 || it.equals(rows.last())) {
                queryStrings.add(list.join("|"))
                list.clear()
            }
        }
        queryStrings
    }

    static def priceMap = Collections.synchronizedMap(new HashMap<String, Object>())

    static def stocks = sql.rows("Select * from stock").stream().map { new Stock(n: it.name, ch: it.ch) }.collect(Collectors.toList())

    @RequestMapping("/stocks")
    def Object getStocks() {
        return stocks
    }

    @RequestMapping("/prices")
    def Object getPrice(@RequestBody List<String> chs) {
        return chs.stream().map { priceMap.get(it + ".tw") }.collect(Collectors.toList())
    }

    @Scheduled(cron = "0/5 * * * * ?")
    def void doParsing() {
        Observable.from(getQueryStrings())
                .flatMap { queryString ->
            Async.start({
                Jsoup.connect("http://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=${queryString}&json=1&delay=0")
                        .cookies(getCookies())
                        .execute().body()
            }, Schedulers.io())
        }
        .map {
            new JsonSlurper().parseText(it)
        }
        .forEach {
            it.msgArray.forEach {
                def price = priceMap.get(it.ch)
                if (price == null) {
                    priceMap.put(it.ch, it)
                } else {
                    if ("${it.tlong}".toLong() > "${price.tlong}".toLong()) {
                        priceMap.put(it.ch, it)
                    }
                }
            }
        }
    }


}
