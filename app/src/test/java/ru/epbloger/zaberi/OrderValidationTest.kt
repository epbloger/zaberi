package ru.epbloger.zaberi

import org.junit.Assert.*
import org.junit.Test

class OrderValidationTest {
    private val base = Order("id","Товар","Магазин",100,0,Status.READY.name,null,"","","","2026-11-25")
    private fun check(order:Order, valid:Boolean, new:Boolean=false) {
        assertEquals(order.toString(),valid,runCatching{if(new)validateNewOrder(order) else validateOrder(order)}.isSuccess)
    }

    @Test fun boundaryCases(){
        check(base,true,true)
        for(name in listOf(""," ","a","x".repeat(200),"x".repeat(201)))check(base.copy(name=name),name.isNotBlank()&&name.length<=200)
        for(paid in listOf(-1L,0L,1L,100L,MAX_ORDER_AMOUNT,MAX_ORDER_AMOUNT+1))for(price in listOf(null,0L,1L,100L,MAX_ORDER_AMOUNT,MAX_ORDER_AMOUNT+1))
            check(base.copy(paid=paid,price=price),paid in 0..MAX_ORDER_AMOUNT&&(price==null||price in paid..MAX_ORDER_AMOUNT))
        for(lat in listOf(null,0.0,85.05112878,86.0,Double.NaN))for(lon in listOf(null,0.0,180.0,181.0,Double.POSITIVE_INFINITY))
            check(base.copy(point="ПВЗ",pointLat=lat,pointLon=lon),validPoint(lat,lon))
        for(status in Status.values())check(base.copy(status=status.name),status in setOf(Status.TRANSIT,Status.READY,Status.RECEIVED),true)
        for(expected in listOf(-1L,0L,50L,100L,101L))for(received in listOf(-1L,0L,50L,100L,101L))
            check(base.copy(paid=100,status=Status.REFUND.name,expectedRefund=expected,receivedRefund=received),expected in 0..100&&received in 0 until expected)
        check(base.copy(created="bad"),false)
        check(base.copy(deadline="2026-02-30"),false)
        check(base.copy(pointLat=0.0,pointLon=0.0),false)
    }
}
