// Package cardservice 暴露内嵌的前端构建产物。
//
// 前端产物位于 web/dist（Vite 构建输出）。未构建时 WebFS 返回 nil，
// HTTP 层会给出明确的"前端未构建"提示而不是白屏。
package cardservice

import (
	"embed"
	"io/fs"
)

//go:embed all:web/dist
var webDist embed.FS

// WebFS 返回前端静态资源根目录；未构建（缺少 index.html）时返回 nil。
func WebFS() fs.FS {
	sub, err := fs.Sub(webDist, "web/dist")
	if err != nil {
		return nil
	}
	if _, err := fs.Stat(sub, "index.html"); err != nil {
		return nil
	}
	return sub
}
