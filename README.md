# A tweet

On 2023-05-23, John A De Goes tweeted:

> Take a wrapper (newtype) structure like:
`case class Box[A](value: A)`
A simple local expression like `Box(42).value` has much more overhead than you would think:
> 1. Allocation of 12 bytes on the heap for Box object header + 4 more bytes for a reference
> 2. Allocation of 12 bytes on the heap for java.lang.Integer object header + 4 more bytes for an integer
> 3. Invocation of a virtual getter method that Scala quietly generates for the `value` field
> 4. Invocation of two separate constructors, one for Box and one for java.lang.Integer
The heap allocations put pressure on the garbage collector, while the access through getter adds indirection overhead (which, thankfully, the JVM can sometimes optimize on its own), and the constructors add jumps.
The net effect of these sources of overhead, but especially the size and quantity of heap allocations, is so significant, that if you’re doing high-performance work in Scala, you have to think long and hard about the costs of abstraction.
If you don’t use abstraction, your code is hard to maintain, low-level, and fragile, and fails to take advantage of the type system of Scala. If you do use abstraction, your code becomes easy to maintain, high-level, and robust, fully taking advantage of Scala’s type system, but suffers from unacceptable overhead in some high-performance scenarios.
One of the most refreshing facts about Rust is that ...

Sounds reasonable, but all that above is more complex. 
The Scala, Java or JVM specification does not say that there will be 12 bytes for such structures! 
Even more, nothing in the JVM specification requires that the JVM will allocate anything on the physical heap, 
and that there will be any real gc use. 
All above is an implementation detail, and while this is  true for most commonly used OpenJDK, 
or Hotspot derivatives as for 2023 it is not valid for all JVMs,  and may not be valid for future versions of OpenJDK/Hotspot. 
More importantly, it is not true today(!!!) for GraalVM, 
which is a more advanced JVM implementation out there, and the case presented is one that that graal optimized a long time ago.


# Examination

I will examine the original claim. We will use Scala 3.3 (but it does not matter) OpenJDK (xxx) and GraalVM (yyy).
I will use `JMH`, and `sbt` - with jmh plugin to measure the performance.


I will use popular [Collatz conjecture](https://en.wikipedia.org/wiki/Collatz_conjecture)  as an example for benchmarking:
```scala
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
```
This is very simple imperative version, that uses primitive values (longs) and primitive operations (add, mul, div, mod).

Value 27 is chosen because it generates 111 internal iterations. This is enough to get some meaningful results. 

Now, I can implement this using `Box class` defined as in John tweet:

```scala
case class Box[A](value: A)
```

```scala 
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
```

Tthis code performs multiple boxing - unboxing operations, which makes it quite a good example.

Next, if You are already using types, you are not really using variables and loops and you are actually using functional programming.
That is why I created more pure variation of the code above (using recursion):

```scala
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
```

Finally, I can also create a variant that uses opaque types (which only works in a modern scala 3.x).
```scala
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
```


# Benchmarking

Finally, I am ready to do some testing. 
The tests were done on my regular desktop (threadripper). 
I did not invest much time into preparing clean environment for testing, and did not even take measurements for a long time - 
but the results should be visible anyhow.

The particular command I used was:

`sbt "jmh:run -prof gc -w 2s -r 5s -wi 3 -i 3 -f 3 jmh.CaseBoxing"`

I added `-prof gc` option (prof in JMH is a powerful tool) in order to show an interesting detail.



# OpenJDK Results 

That is what I got when I started those examples under openjdk 17.0.6:

```
openjdk version "20.0.1" 2023-04-18
OpenJDK Runtime Environment (build 20.0.1+9-29)
OpenJDK 64-Bit Server VM (build 20.0.1+9-29, mixed mode, sharing)


[info] Benchmark                                         Mode  Cnt     Score     Error   Units
[info] CaseBoxing.boxCollatz                             avgt    9   669.842 ±  18.420   ns/op
[info] CaseBoxing.boxCollatz:·gc.alloc.rate              avgt    9  5285.721 ± 145.687  MB/sec
[info] CaseBoxing.boxCollatz:·gc.alloc.rate.norm         avgt    9  3712.000 ±   0.001    B/op
[info] CaseBoxing.boxCollatz:·gc.count                   avgt    9   292.000            counts
[info] CaseBoxing.boxCollatz:·gc.time                    avgt    9   721.000                ms
[info] CaseBoxing.boxCollatzR                            avgt    9   657.624 ±  13.385   ns/op
[info] CaseBoxing.boxCollatzR:·gc.alloc.rate             avgt    9  5383.351 ± 109.478  MB/sec
[info] CaseBoxing.boxCollatzR:·gc.alloc.rate.norm        avgt    9  3712.000 ±   0.001    B/op
[info] CaseBoxing.boxCollatzR:·gc.count                  avgt    9   295.000            counts
[info] CaseBoxing.boxCollatzR:·gc.time                   avgt    9   677.000                ms
[info] CaseBoxing.longCollatz                            avgt    9   102.574 ±   2.663   ns/op
[info] CaseBoxing.longCollatz:·gc.alloc.rate             avgt    9    ≈ 10⁻⁴            MB/sec
[info] CaseBoxing.longCollatz:·gc.alloc.rate.norm        avgt    9    ≈ 10⁻⁵              B/op
[info] CaseBoxing.longCollatz:·gc.count                  avgt    9       ≈ 0            counts
[info] CaseBoxing.opaqueBoxCollatzR                      avgt    9   103.874 ±   3.162   ns/op
[info] CaseBoxing.opaqueBoxCollatzR:·gc.alloc.rate       avgt    9    ≈ 10⁻⁴            MB/sec
[info] CaseBoxing.opaqueBoxCollatzR:·gc.alloc.rate.norm  avgt    9    ≈ 10⁻⁵              B/op
[info] CaseBoxing.opaqueBoxCollatzR:·gc.count            avgt    9       ≈ 0            counts
```

This is exactly a result that John De Goes is describing. We have a simple imperative version
that takes 102 ns per run. And all the wrappers induce a visible cost resulting in ~ 670 ns per operation.
Over 6 (almost 7) times more. The notable exception is a Scala 3 use of opaque type which gives results similar 
to an imperative code. Moreover, it is clearly visible that in all the "functional" solution were putting a stress on a garbage collector
that was performing more than 5MB allocations per second.

# Graal
The question is what happens if I run the same benchmarks on graal (22.3.1 - just a version I had at the moment of writing).


```
java version "17.0.6" 2023-01-17 LTS
Java(TM) SE Runtime Environment GraalVM EE 22.3.1 (build 17.0.6+9-LTS-jvmci-22.3-b11)
Java HotSpot(TM) 64-Bit Server VM GraalVM EE 22.3.1 (build 17.0.6+9-LTS-jvmci-22.3-b11, mixed mode, sharing)


[info] CaseBoxing.boxCollatz                             avgt    9  72.133 ±  5.547   ns/op
[info] CaseBoxing.boxCollatz:·gc.alloc.rate              avgt    9   0.001 ±  0.002  MB/sec
[info] CaseBoxing.boxCollatz:·gc.alloc.rate.norm         avgt    9  ≈ 10⁻⁴             B/op
[info] CaseBoxing.boxCollatz:·gc.count                   avgt    9     ≈ 0           counts
[info] CaseBoxing.boxCollatzR                            avgt    9  68.404 ±  1.571   ns/op
[info] CaseBoxing.boxCollatzR:·gc.alloc.rate             avgt    9   0.001 ±  0.002  MB/sec
[info] CaseBoxing.boxCollatzR:·gc.alloc.rate.norm        avgt    9  ≈ 10⁻⁴             B/op
[info] CaseBoxing.boxCollatzR:·gc.count                  avgt    9     ≈ 0           counts
[info] CaseBoxing.longCollatz                            avgt    9  63.048 ±  0.804   ns/op
[info] CaseBoxing.longCollatz:·gc.alloc.rate             avgt    9   0.001 ±  0.002  MB/sec
[info] CaseBoxing.longCollatz:·gc.alloc.rate.norm        avgt    9  ≈ 10⁻⁴             B/op
[info] CaseBoxing.longCollatz:·gc.count                  avgt    9     ≈ 0           counts
[info] CaseBoxing.opaqueBoxCollatzR                      avgt    9  62.978 ±  0.870   ns/op
[info] CaseBoxing.opaqueBoxCollatzR:·gc.alloc.rate       avgt    9   0.001 ±  0.002  MB/sec
[info] CaseBoxing.opaqueBoxCollatzR:·gc.alloc.rate.norm  avgt    9  ≈ 10⁻⁴             B/op
[info] CaseBoxing.opaqueBoxCollatzR:·gc.count            avgt    9     ≈ 0           counts
[success] Total time: 258 s (04:18), completed May 29, 2023, 7:36:51 PM

```

It is visible that all the methods are more or less equal (and faster than OpenJDK case).

The reason for that is quite visible in `gc.alloc.rate` -> there were no allocations(!!!).
Yes, graal was smart enough to find out that the code does not need any.
What is important, all this happened on the JIT level. The allocations were in Scala code,
they were still visible in the bytecode (that is why analysing bytecode is quite often misleading).
But the JIT (Graal) was able to eliminate them.

# Conclusion

I wanted to show that in order to demonstrate  2 things:
a) Functional code does not have to be slow - it is a matter of ever improving compilers, runtimes and platforms.
I do agree that currently (2023) our compilers (generally) still have a lot of room for improvements. So if someone is indeed fighting for cycles 
it is probably wise to sometimes go back in some fragments of code into imperative style. 
However, the more FP we do the more pressure we put on vendors to provide compilers that are able to optimize such code (that is exactly 
the case for graal).
b) It is very easy to spread performance myths - just by sending statements that seem obvious and are actually true in some contexts.
But they are not true in all contexts - and times, machines, compilers, runtimes, platforms are changing. The problem is that myths prevail
and quite often become widespread just after they are in not valid anymore. So be careful when you state some absolute truths about performance,
be suspiciou when you read them. And sometimes just try to verify.
