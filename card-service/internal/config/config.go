// Package config 负责从环境变量加载运行配置，并对生产必需项做 fail-closed 校验。
package config

import (
	"errors"
	"fmt"
	"net"
	"os"
	"strconv"
	"strings"
)

// Config 是服务运行配置，全部来自 CARDSERVICE_* 环境变量。
type Config struct {
	Addr                   string
	DBPath                 string
	BootstrapAdminUsername string
	BootstrapAdminPassword string
	BootstrapAdminDisplay  string
	CookieSecure           bool
	TrustedProxies         []string
	TrustedNets            []*net.IPNet
	LogLevel               string
	AccessLog              bool
	RateLimitPerMinute     int
}

// Load 读取环境变量并校验。
func Load() (*Config, error) {
	c := &Config{
		Addr:                   env("CARDSERVICE_ADDR", "127.0.0.1:8090"),
		DBPath:                 env("CARDSERVICE_DB_PATH", "./data/cardservice.db"),
		BootstrapAdminUsername: strings.TrimSpace(env("CARDSERVICE_BOOTSTRAP_ADMIN_USERNAME", "")),
		BootstrapAdminPassword: env("CARDSERVICE_BOOTSTRAP_ADMIN_PASSWORD", ""),
		BootstrapAdminDisplay:  env("CARDSERVICE_BOOTSTRAP_ADMIN_DISPLAY", "超级管理员"),
		LogLevel:               env("CARDSERVICE_LOG_LEVEL", "info"),
		AccessLog:              envBool("CARDSERVICE_ACCESS_LOG", true),
		RateLimitPerMinute:     envInt("CARDSERVICE_RATE_LIMIT_GLOBAL_PER_MINUTE", 600),
	}
	c.CookieSecure = envBool("CARDSERVICE_COOKIE_SECURE", true)

	raw := env("CARDSERVICE_TRUSTED_PROXIES", "127.0.0.1/32,::1/128")
	for _, part := range strings.Split(raw, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		if _, ipnet, err := net.ParseCIDR(part); err == nil {
			c.TrustedProxies = append(c.TrustedProxies, part)
			c.TrustedNets = append(c.TrustedNets, ipnet)
			continue
		}
		ip := net.ParseIP(part)
		if ip == nil {
			return nil, fmt.Errorf("CARDSERVICE_TRUSTED_PROXIES 含非法项: %q", part)
		}
		bits := 32
		if ip.To4() == nil {
			bits = 128
		}
		_, ipnet, _ := net.ParseCIDR(fmt.Sprintf("%s/%d", ip.String(), bits))
		c.TrustedProxies = append(c.TrustedProxies, ipnet.String())
		c.TrustedNets = append(c.TrustedNets, ipnet)
	}

	if c.Addr == "" {
		return nil, errors.New("CARDSERVICE_ADDR 不能为空")
	}
	if c.DBPath == "" {
		return nil, errors.New("CARDSERVICE_DB_PATH 不能为空")
	}
	if c.BootstrapAdminPassword != "" && len([]rune(c.BootstrapAdminPassword)) < 10 {
		return nil, errors.New("CARDSERVICE_BOOTSTRAP_ADMIN_PASSWORD 至少 10 位")
	}
	if c.BootstrapAdminPassword == "" && c.BootstrapAdminUsername != "" {
		return nil, errors.New("设置了 CARDSERVICE_BOOTSTRAP_ADMIN_USERNAME 就必须同时设置 CARDSERVICE_BOOTSTRAP_ADMIN_PASSWORD")
	}
	return c, nil
}

// IsTrustedProxy 判断某 IP 是否属于可信代理（只有可信代理发来的 X-Forwarded-For 才会被采信）。
func (c *Config) IsTrustedProxy(ip net.IP) bool {
	if ip == nil {
		return false
	}
	for _, n := range c.TrustedNets {
		if n.Contains(ip) {
			return true
		}
	}
	return false
}

func env(key, def string) string {
	if v, ok := os.LookupEnv(key); ok {
		return v
	}
	return def
}

func envBool(key string, def bool) bool {
	v, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(v) == "" {
		return def
	}
	switch strings.ToLower(strings.TrimSpace(v)) {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	default:
		return def
	}
}

func envInt(key string, def int) int {
	v, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(v) == "" {
		return def
	}
	n, err := strconv.Atoi(strings.TrimSpace(v))
	if err != nil {
		return def
	}
	return n
}
