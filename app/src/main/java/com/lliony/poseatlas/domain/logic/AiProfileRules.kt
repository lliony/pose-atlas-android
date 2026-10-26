package com.lliony.poseatlas.domain.logic

import com.lliony.poseatlas.data.ai.AiConfiguration
import com.lliony.poseatlas.data.ai.AiProfile

/**
 * AI 模型档案的纯规则：在持久化边界前完成校验、规范化与状态变换，零 IO 可单测。
 * 依赖 data.ai 的两个值类型——其类型归属由 C5 移入 domain 时，此处的引用随之收敛。
 */
object AiProfileRules {
    /** 校验必填字段并规范化 baseUrl；任一为空抛 [IllegalArgumentException]。 */
    fun normalize(profile: AiProfile): AiProfile {
        require(profile.name.isNotBlank() && profile.model.isNotBlank() && profile.apiKey.isNotBlank()) { "请填写名称、模型和 API key" }
        return profile.copy(baseUrl = normalizeAiUrl(profile.baseUrl))
    }

    /** upsert：按 id 去重复写该档案；recommendId/draftId 仅当为空时默认指向新档案。 */
    fun upsert(config: AiConfiguration, profile: AiProfile): AiConfiguration = config.copy(
        profiles = config.profiles.filterNot { it.id == profile.id } + profile,
        recommendId = config.recommendId ?: profile.id,
        draftId = config.draftId ?: profile.id,
    )

    /** delete：移除档案，同时清掉指向它的 recommendId/draftId。 */
    fun delete(config: AiConfiguration, id: String): AiConfiguration = config.copy(
        profiles = config.profiles.filterNot { it.id == id },
        recommendId = config.recommendId.takeUnless { it == id },
        draftId = config.draftId.takeUnless { it == id },
    )
}