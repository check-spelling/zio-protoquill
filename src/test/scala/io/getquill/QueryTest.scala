package io.getquill

import scala.language.implicitConversions
import miniquill.quoter.Dsl._
import miniquill.quoter.Quoted
import miniquill.quoter._
import io.getquill._
import io.getquill.ast._
import miniquill.quoter.QuotationLot
import miniquill.quoter.QuotationVase
import io.getquill.context.ExecutionType
import org.scalatest._

class QueryTest extends Spec with Inside { //hellooooooo

  // TODO Need to test 3-level injection etc...
  case class Address(street:String, zip:Int) extends Embedded
  case class Person(name: String, age: Int, address: Address)
  val sqlCtx = new MirrorContext(MirrorSqlDialect, Literal)
  val ctx = new MirrorContext(MirrorIdiom, Literal)

  inline def people = quote {
    query[Person]
  }
  inline def addresses = quote {
    people.map(p => p.address)
  }
  def peopleRuntime = quote {
    query[Person]
  }
  // TODO Need join semantics to test these (also add id, fk to case classes)
  // def addressesRuntimePeopleCompiletime = quote {
  //  peopleRuntime.join(address).on(_.name == _.street)
  // }
  // def addressesCompiletimePeopleRuntime = quote {
  //  peopleRuntime.join(addressRuntime).on(_.name == _.street)
  // }
  def addressesRuntime = quote {
    peopleRuntime.map(p => p.address)
  }


  // one level object query
  // @Test
  def oneLevelQuery(): Unit = {
    
  }

  // nested object query (person with address)
  ("Query with embedded entity") - {
    ("should express embedded props as x.y.z with MirrorContext") - {
      import ctx._
      "with full expansion" in {
        val result = ctx.run(people)
        result.string mustEqual """querySchema("Person").map(x => (x.name, x.age, x.address.street, x.address.zip))"""
        result.executionType mustEqual ExecutionType.Static
      }
      "with field select and lift" in {
        inline def extQuery = quote(addresses.map(a => a.street + lift("-ext"))) 
        val result = ctx.run(extQuery)
        result.string mustEqual """querySchema("Person").map(p => p.address.street + ?)"""
        result.executionType mustEqual ExecutionType.Static
      }
    }
    ("should express embedded props as x.z in SqlMirrorContext") - {
      import sqlCtx._
      "with full expansion" in {
        val result = sqlCtx.run(people)
        result.string mustEqual "SELECT x.name, x.age, x.street, x.zip FROM Person x"
        result.executionType mustEqual ExecutionType.Static
      }
      "with field select and list" in {
        inline def extQuery = quote(addresses.map(a => a.street + lift("-ext"))) 
        val result = sqlCtx.run(extQuery)
        result.string mustEqual "SELECT p.street || ? FROM Person p"
        result.executionType mustEqual ExecutionType.Static
      }
    }
  }

  // @Test
  ("simple query mapping") - {
    ("should work with MirrorContext") in {
      import ctx._
      val result = ctx.run(people.map(p => p.name))
      result.string mustEqual """querySchema("Person").map(p => p.name)"""
      result.executionType mustEqual ExecutionType.Static
    }
    "should work with SqlMirrorContext" in {
      import sqlCtx._
      val result = sqlCtx.run(people.map(p => p.name))
      result.string mustEqual "SELECT p.name FROM Person p"
      result.executionType mustEqual ExecutionType.Static
    }
  }

  // person with address mapping to address
  // @Test
  ("returning a whole embdded entity") - { //hello
    ("should expand with MirrorContext") in {
      import ctx._
      val result = ctx.run(addresses)
      result.string mustEqual """querySchema("Person").map(p => (p.address.street, p.address.zip))"""
      result.executionType mustEqual ExecutionType.Static
    }
    ("should expand with SqlMirrorContext") in {
      import sqlCtx._
      val result = sqlCtx.run(addresses)
      result.string mustEqual "SELECT p.street, p.zip FROM Person p"
      result.executionType mustEqual ExecutionType.Static
    }
  }


  // @Test
  ("runtime query") - {
    ("should be the same as compile-time when not referencing anything else") in {
      inside(peopleRuntime) { case Quoted(Entity(entityName, List()), List(), _) => 
        entityName mustEqual "Person"
      }
    }

    ("should contain a QuotationVase when referencing a runtime query") in {
      inside(addressesRuntime) { 
        case Quoted(
          Map(QuotationTag(_), Ident("p"), Property(Ident("p"), "address")), 
          List(),
          List(QuotationVase(Quoted(Entity("Person", List()), List(), _), _))
        ) =>
      }
    }

    "reference should express correct in MirrorContext" in {
      import ctx._
      val result = ctx.run(addressesRuntime)
      result.string mustEqual """querySchema("Person").map(p => (p.address.street, p.address.zip))"""
      result.executionType mustEqual ExecutionType.Dynamic
    }
    "reference should express correct in SqlMirrorContext" in {
      import sqlCtx._
      val result = sqlCtx.run(addressesRuntime)
      result.string mustEqual "SELECT p.street, p.zip FROM Person p"
      result.executionType mustEqual ExecutionType.Dynamic
    }
    "shuold work correctly with lift" in {
      import ctx._
      val result = ctx.run(peopleRuntime.map(p => p.name + lift("hello")))
      result.string mustEqual """querySchema("Person").map(p => p.name + ?)"""
      result.executionType mustEqual ExecutionType.Dynamic
    }
    "two-level shuold work correctly with lift" in {
      import ctx._
      def addressesRuntimeAndLift = quote {
        peopleRuntime.map(p => p.address.street + lift("hello"))
      }
      printer.lnf(addressesRuntimeAndLift)
      val result = ctx.run(addressesRuntimeAndLift)
      result.string mustEqual """querySchema("Person").map(p => p.address.street + ?)"""
      result.executionType mustEqual ExecutionType.Dynamic
    }
  }

}