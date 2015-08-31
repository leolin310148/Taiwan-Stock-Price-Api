import groovy.json.JsonSlurper
import groovy.sql.Sql

def sql = Sql.newInstance(
        'jdbc:mysql://10.0.1.50/Stock?useUnicode=true&characterEncoding=UTF-8',
        'root',
        'ro@t',
        'com.mysql.jdbc.Driver'
)

sql.execute("TRUNCATE TABLE industry")
sql.execute("TRUNCATE TABLE stock")
def parseText = new JsonSlurper().parseText(new URL("http://mis.twse.com.tw/stock/api/getIndustry.jsp").text)
parseText.otc.forEach {
    sql.executeInsert("INSERT into industry VALUES ('otc',?,?)", [it.code, it.name])
}
parseText.tse.forEach {
    sql.executeInsert("INSERT into industry VALUES ('tse',?,?)", [it.code, it.name])
}

sql.eachRow("SELECT * FROM industry", { industry ->
    def parseStocks = new JsonSlurper().parseText(new URL("http://mis.twse.com.tw/stock/api/getCategory.jsp?ex=${industry.market}&i=${industry.industry}").text)
    parseStocks.msgArray.toList().stream().filter { it.ch.length() < 8 }.forEach { stock ->
        try {
            sql.executeInsert("INSERT into stock VALUES (?,?,?,?)", [stock.ch, stock.n, industry.industry, industry.market])
        } catch (e) {
        }
    }
})

