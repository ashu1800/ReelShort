// Package migrations 以 embed 方式携带 SQLite 迁移脚本，保证单二进制部署。
package migrations

import "embed"

// FS 包含全部迁移脚本，文件名形如 0001_init.sql。
//
//go:embed *.sql
var FS embed.FS
