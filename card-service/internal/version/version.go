// Package version 暴露编译期注入的版本信息。
package version

// Version 由构建脚本通过 -ldflags "-X card.hjj888.cc/cardservice/internal/version.Version=..." 注入。
var Version = "0.1.0-dev"

// BuildTime 由构建脚本注入，未注入时为空。
var BuildTime = ""
