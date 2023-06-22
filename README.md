# A tweet

On 2023-05-23, John A De Goes tweeted:

> Take a wrapper (newtype) structure like:
`case class Box[A](value: A)`
A simple local expression like `Box(42).value` has much more overhead than you would think:
1. Allocation of 12 bytes on the heap for Box object header + 4 more bytes for a reference
2. Allocation of 12 bytes on the heap for java.lang.Integer object header + 4 more bytes for an integer
3. Invocation of a virtual getter method that Scala quietly generates for the `value` field
4. Invocation of two separate constructors, one for Box and one for java.lang.Integer
The heap allocations put pressure on the garbage collector, while the access through getter adds indirection overhead (which, thankfully, the JVM can sometimes optimize on its own), and the constructors add jumps.
The net effect of these sources of overhead, but especially the size and quantity of heap allocations, is so significant, that if you’re doing high-performance work in Scala, you have to think long and hard about the costs of abstraction.
If you don’t use abstraction, your code is hard to maintain, low-level, and fragile, and fails to take advantage of the type system of Scala. If you do use abstraction, your code becomes easy to maintain, high-level, and robust, fully taking advantage of Scala’s type system, but suffers from unacceptable overhead in some high-performance scenarios.
One of the most refreshing facts about Rust is that ...

Let me stop you right there and explain that all that above is not exactly true.
Nowhere in the Scala, Java or JVM spec does it say that there will be 12 bytes for such structures, etc.
More, nothing in JVM requires that the JVM will allocate anything on the physical heap and there will be any real gc use.
All above is an implementation detail, and while, this is actually true for most commonly used OpenJDK, or Hotspot derivatives as for 2023- it is not true for all JVMs.
This may not be true for OpenJDK or Hotspot in the future.
More importantly, it is not true for GraalVM, which is more advanced JVM implementation out there, and the case
presented is actually one that graal has optimized long time ago.

# Performance myth

What we have here is an observation, that holds in some context, but is not true in general. The problem with 
publishing such observations is that they quickly become popular and are repeated over and over again.
Quite often they become accepted truths among young developers, who do not question such statements.
More funny, they quite often become accepted after they are no longer true.

When I was learning assembly for Motorola 68k in 1990s, I was told that branch instructions are slower when branch is taken and 
if possible code should be written in a way so that branch is not taken. This was true for 68000 but was not true for more modern 
revisions of this CPU family, that were already used in modern computers at that time. Fallowing this myth I was actually 
creating code that was SLOWER on modern machines.

# Examination

So let's examine the claim. We will use Scala 3.3 (but it does not matter) OpenJDK (xxx) and GraalVM (yyy).
We will use JMH, and sbt - with jmh plugin to measure performance.



Let's write a proper benchmark. We will use popular [Collatz conjecture](https://en.wikipedia.org/wiki/Collatz_conjecture)  as an example:
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

initial 27 is chosen so that we have 111 internal iterations. This is enough to get some meaningful results. 

We can implement this using Box class defined as in John tweet:

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

as you see this code does multiple boxing - unboxing operations, and it is a good example of what we want to measure.

Practically, if You are already using types at that level, you are not really using variables and loops.
Let's create more pure variation of the code above (using recursion):

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

# Benchmarking


`sbt "jmh:run -prof gc -w 2s -r 5s -wi 3 -i 3 -f 3 jmh.CaseBoxing"`


# Results 

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
# costOfBoxingScala
