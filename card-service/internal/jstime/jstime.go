// Package jstime 统一处理"上海时区"的时间格式化与解析。
//
// 卡密服务面向中文用户，所有对外展示的时间都是 Asia/Shanghai（UTC+8）。
// 数据库统一存 unix 秒（UTC），只有展示与按天聚合时才转换。
package jstime

import (
	"errors"
	"strings"
	"sync"
	"time"
)

// Layout 是对外统一的时间格式，易语言客户端直接展示，不要改。
const Layout = "2006-01-02 15:04:05"

// DateLayout 是日期格式，用于筛选与按天聚合。
const DateLayout = "2006-01-02"

var (
	locOnce sync.Once
	loc     *time.Location
)

// Location 返回上海时区；tzdata 缺失时退回固定 +8 时区（中国自 1991 年起无夏令时）。
func Location() *time.Location {
	locOnce.Do(func() {
		l, err := time.LoadLocation("Asia/Shanghai")
		if err != nil {
			l = time.FixedZone("CST", 8*3600)
		}
		loc = l
	})
	return loc
}

// Format 把 unix 秒格式化为 "YYYY-MM-DD HH:MM:SS"；unix <= 0 返回空字符串（对外不返回 null）。
func Format(unix int64) string {
	if unix <= 0 {
		return ""
	}
	return time.Unix(unix, 0).In(Location()).Format(Layout)
}

// FormatTime 把 time.Time 格式化到上海时区。
func FormatTime(t time.Time) string {
	return t.In(Location()).Format(Layout)
}

// Now 返回当前时间（本地时钟）。
func Now() time.Time { return time.Now() }

// Parse 解析 "YYYY-MM-DD HH:MM:SS"（上海时区）。
func Parse(s string) (time.Time, error) {
	return time.ParseInLocation(Layout, s, Location())
}

// ParseDate 解析 "YYYY-MM-DD" 为上海时区当天 00:00:00。
func ParseDate(s string) (time.Time, error) {
	if s == "" {
		return time.Time{}, errors.New("empty date")
	}
	return time.ParseInLocation(DateLayout, s, Location())
}

// ParseFlexible 同时接受 "YYYY-MM-DD" 与 "YYYY-MM-DD HH:MM:SS"。
// 返回的 bool 表示输入是否是"纯日期"（区间上界按天处理时需要区分）。
func ParseFlexible(s string) (time.Time, bool, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return time.Time{}, false, errors.New("empty time")
	}
	if len(s) == len(DateLayout) {
		t, err := time.ParseInLocation(DateLayout, s, Location())
		return t, true, err
	}
	t, err := time.ParseInLocation(Layout, s, Location())
	return t, false, err
}

// DayRange 返回 unix 秒所属"上海自然日"的起止 unix 秒（含起、不含止）。
func DayRange(unix int64) (int64, int64) {
	t := time.Unix(unix, 0).In(Location())
	start := time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, Location())
	return start.Unix(), start.AddDate(0, 0, 1).Unix()
}

// DayKey 返回 unix 秒对应的上海时区日期字符串（用于按天聚合与趋势图）。
func DayKey(unix int64) string {
	return time.Unix(unix, 0).In(Location()).Format(DateLayout)
}
