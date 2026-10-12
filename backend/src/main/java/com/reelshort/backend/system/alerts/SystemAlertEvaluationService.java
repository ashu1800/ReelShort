package com.reelshort.backend.system.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.reelshort.backend.system.runtime.SystemRuntimeService;

/**
 * P3-20：把告警评估挂到定时任务，而不是只靠管理员手动点击“评估”。
 *
 * <p>{@link SystemAlertService#evaluate} 现在按水位线判定内容源诊断事件，定时评估可以让
 * 内容源恢复后 OPEN 的告警自动 resolve，避免长期粘滞。
 */
@Service
public class SystemAlertEvaluationService {

	private static final Logger log = LoggerFactory.getLogger(SystemAlertEvaluationService.class);

	private final SystemAlertService systemAlertService;
	private final SystemRuntimeService systemRuntimeService;

	public SystemAlertEvaluationService(SystemAlertService systemAlertService,
			SystemRuntimeService systemRuntimeService) {
		this.systemAlertService = systemAlertService;
		this.systemRuntimeService = systemRuntimeService;
	}

	@Scheduled(
			initialDelayString = "${reelshort.system.alerts.evaluation-initial-delay:1m}",
			fixedDelayString = "${reelshort.system.alerts.evaluation-interval:5m}")
	public void evaluateScheduled() {
		try {
			systemAlertService.evaluate(systemRuntimeService.snapshot());
		}
		catch (RuntimeException exception) {
			// 定时评估失败不应影响调度线程，只记录类型与摘要。
			log.warn("Scheduled system alert evaluation failed: {}", exception.getClass().getSimpleName());
		}
	}
}
