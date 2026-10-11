package io.github.kwrld3939.minispring.context.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.kwrld3939.minispring.context.MiniApplicationContext;

/**
 * 并发契约（ADR-0006）：冻结后的容器读路径必须安全。
 *
 * <ul>
 *   <li>单例：所有线程拿到同一个实例，且不抛异常；</li>
 *   <li>prototype：创建过程不触碰共享可变状态，因此并发创建必须全部成功且实例互不相同。</li>
 * </ul>
 */
class ConcurrencyTests {

	private static final String FIXTURE_PACKAGE = ConcurrencyTests.class.getPackageName();
	private static final int THREADS = 16;
	private static final int TASKS_PER_THREAD = 20;
	private static final int TOTAL_TASKS = THREADS * TASKS_PER_THREAD;

	@Test
	void singletonGetBeanReturnsTheSameInstanceAcrossThreads() throws Exception {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			Object expected = context.getBean("singletonComponent");

			List<Object> results = runConcurrently(() -> context.getBean("singletonComponent"));

			assertEquals(TOTAL_TASKS, results.size());
			for (Object result : results) {
				assertSame(expected, result);
			}
		}
	}

	@Test
	void prototypeGetBeanCreatesDistinctInstancesConcurrently() throws Exception {
		try (MiniApplicationContext context = new MiniApplicationContext(FIXTURE_PACKAGE)) {
			List<Object> results = runConcurrently(() -> context.getBean("prototypeComponent"));

			assertEquals(TOTAL_TASKS, results.size());
			assertEquals(TOTAL_TASKS, Set.copyOf(results).size(), "并发创建的 prototype 实例必须互不相同");
		}
	}

	private List<Object> runConcurrently(Callable<Object> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CountDownLatch startGate = new CountDownLatch(1);
		List<Future<Object>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < TOTAL_TASKS; i++) {
				futures.add(pool.submit(() -> {
					startGate.await();
					return task.call();
				}));
			}
			startGate.countDown();
			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}
}
