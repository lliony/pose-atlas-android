package com.lliony.poseatlas.domain.logic

// 工作区命名操作的纯规则：只做列表变换，零数据库依赖，可被 WorkspaceTest 直接覆盖。
object WorkspaceRules {
    /** 把含 [id] 的元素在列表里向上/向下换一位；已在顶(up)或底(down)时原样返回。*/
    fun <T> moveInList(list: List<T>, id: String, up: Boolean, idOf: (T) -> String): List<T> {
        val i = list.indexOfFirst { idOf(it) == id }
        if (i < 0) return list
        val target = when { up -> i - 1; else -> i + 1 }
        if (target < 0 || target >= list.size) return list
        val out = list.toMutableList()
        out[i] = out[target]; out[target] = list[i]
        return out
    }

    /** 把新增的卡 id 追加进现有册成员并去重，保持既有顺序。*/
    fun addCardIds(current: List<String>, new: List<String>) = (current + new).distinct()
    /** 从册成员里移除给定的卡 id。*/
    fun removeCardIds(current: List<String>, ids: List<String>) = current.filterNot { it in ids }
}