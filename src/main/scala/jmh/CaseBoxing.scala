package jmh

import org.openjdk.jmh.annotations.*
import scala.language.adhocExtensions

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class CaseBoxing :
  @Benchmark
  def boxCollatz(): Long =
    var n = Box(27L)
    while (n.value > 1) {
      if (n.value % 2 == 0) {
        n = Box(n.value / 2)
      } else {
        n = Box((3 * n.value + 1))
      }
    }
    n.value

  @Benchmark
  def boxCollatzR(): Long =
    def go(n: Box[Long]): Box[Long] =
      if (n.value > 1) {
        if (n.value % 2 == 0) {
          go(Box(n.value / 2))
        } else {
          go(Box(3 * n.value + 1))
        }
      } else {
        n
      }
    go(Box(27L)).value

  @Benchmark
  def opaqueBoxCollatzR() :Long  =
    def go(n: OpaqueBox[Long]): OpaqueBox[Long] =
      if (n.value > 1) {
        if (n.value % 2 == 0) {
          go(OpaqueBox(n.value / 2))
        } else {
          go(OpaqueBox(3 * n.value + 1))
        }
      } else {
        n
      }
    go(OpaqueBox(27L)).value

  @Benchmark
  def longCollatz(): Long =
    var n = 27L
    while (n > 1) {
      if (n % 2 == 0) {
        n = n / 2
      } else {
        n = 3 * n + 1
      }
    }
    n

case class Box[A](value: A)

opaque type OpaqueBox[A] = A

object OpaqueBox:
  def apply[A](value: A): OpaqueBox[A] = value

extension[A] (x:OpaqueBox[A])
  def value: A = x

