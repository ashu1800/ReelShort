// 卡密服务入口。
//
// 用法：
//
//	cardservice                     启动 HTTP 服务
//	cardservice -version            打印版本
//	cardservice -backup /var/backups/cardservice   执行一次一致性备份后退出
//	cardservice -reset-admin-password admin        重置管理员口令并打印新口令后退出
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"
	_ "time/tzdata" // 内嵌时区库，保证静态二进制也能解析 Asia/Shanghai

	"card.hjj888.cc/cardservice/internal/config"
	"card.hjj888.cc/cardservice/internal/httpapi"
	"card.hjj888.cc/cardservice/internal/service"
	"card.hjj888.cc/cardservice/internal/store"
	"card.hjj888.cc/cardservice/internal/version"
)

func main() {
	var (
		showVersion   = flag.Bool("version", false, "打印版本号后退出")
		backupDir     = flag.String("backup", "", "执行一次数据库备份到指定目录后退出")
		backupKeep    = flag.Int("backup-keep", 14, "备份保留份数")
		resetUsername = flag.String("reset-admin-password", "", "重置指定管理员的口令并打印新口令后退出")
	)
	flag.Parse()

	if *showVersion {
		fmt.Printf("cardservice %s\n", version.Version)
		return
	}

	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "配置错误: %v\n", err)
		os.Exit(1)
	}
	logger := newLogger(cfg.LogLevel)
	slog.SetDefault(logger)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	st, err := store.Open(cfg.DBPath)
	if err != nil {
		logger.Error("打开数据库失败", "error", err, "path", cfg.DBPath)
		os.Exit(1)
	}
	defer func() { _ = st.Close() }()

	if err := st.Migrate(ctx); err != nil {
		logger.Error("数据库迁移失败", "error", err)
		os.Exit(1)
	}

	svc := service.New(st, logger)
	if err := svc.Bootstrap(ctx, cfg); err != nil {
		logger.Error("初始化失败", "error", err)
		os.Exit(1)
	}

	if *resetUsername != "" {
		password, err := svc.AdminResetPassword(ctx, *resetUsername)
		if err != nil {
			logger.Error("重置口令失败", "error", err)
			os.Exit(1)
		}
		fmt.Printf("管理员 %s 的新口令：%s\n（首次登录后必须修改）\n", *resetUsername, password)
		return
	}

	if *backupDir != "" {
		path, err := svc.RunBackup(ctx, *backupDir, *backupKeep)
		if err != nil {
			logger.Error("备份失败", "error", err)
			os.Exit(1)
		}
		fmt.Println(path)
		return
	}

	svc.StartJanitor(ctx)

	handler := httpapi.NewServer(svc, cfg, logger).Handler()
	srv := &http.Server{
		Addr:              cfg.Addr,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       90 * time.Second,
		MaxHeaderBytes:    1 << 20,
	}

	go func() {
		logger.Info("卡密服务已启动",
			"addr", cfg.Addr, "version", version.Version, "db", cfg.DBPath,
			"trusted_proxies", strings.Join(cfg.TrustedProxies, ","))
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("HTTP 服务异常退出", "error", err)
			stop()
		}
	}()

	<-ctx.Done()
	logger.Info("正在停止卡密服务…")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Warn("优雅停止超时", "error", err)
	}
	logger.Info("卡密服务已停止")
}

func newLogger(level string) *slog.Logger {
	var lv slog.Level
	switch strings.ToLower(strings.TrimSpace(level)) {
	case "debug":
		lv = slog.LevelDebug
	case "warn", "warning":
		lv = slog.LevelWarn
	case "error":
		lv = slog.LevelError
	default:
		lv = slog.LevelInfo
	}
	handler := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: lv})
	return slog.New(handler)
}
