package com.example.barandgrillpos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String? = null,
    val name: String,
    @SerialName("parent_name") val parentName: String? = null,
    val created_at: String? = null
)
