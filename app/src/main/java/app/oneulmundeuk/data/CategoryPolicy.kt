package app.oneulmundeuk.data

import app.oneulmundeuk.data.db.CategoryEntity
import app.oneulmundeuk.data.db.isArchived
import app.oneulmundeuk.data.model.RecordWithCategory

/**
 * Category archive policy — "새 기록에서는 사라지지만, 과거 기록의 의미는 보존한다."
 *  - Writing (Create / Edit): active categories only. An archived category stays only as the record's current value
 *    while it is still selected (Edit of an old record); once changed away it is not offered again.
 *  - Looking back (Records filter): active + archived ones that some record still uses.
 *  - Explore / search: not filtered at all — archived-category records stay in the corpus, topics and suggestions.
 */
object CategoryPolicy {
    fun editorOptions(categories: List<CategoryEntity>, selectedId: String?): List<CategoryEntity> =
        categories.filter { !it.isArchived || it.id == selectedId }

    /** May a new record (or a restored draft) start with this category? */
    fun selectableForNew(categories: List<CategoryEntity>, id: String): Boolean =
        categories.any { it.id == id && !it.isArchived }

    fun recordsFilterOptions(categories: List<CategoryEntity>, records: List<RecordWithCategory>): List<CategoryEntity> {
        val used = records.mapNotNullTo(HashSet()) { it.record.categoryId }
        return categories.filter { !it.isArchived || it.id in used }
    }

    /** Settings / 카테고리 관리: the categories a user can still choose (and delete). */
    fun active(categories: List<CategoryEntity>): List<CategoryEntity> = categories.filter { !it.isArchived }

    /**
     * A new category name: trimmed, not blank, not taken. The name column is unique across *all* rows, so a name an
     * archived category still holds cannot be reused (no restore in the MVP) — it is refused, never crashes.
     */
    fun checkNewName(input: String, categories: List<CategoryEntity>): NewCategoryName {
        val name = input.trim()
        if (name.isEmpty()) return NewCategoryName.Blank
        val same = categories.firstOrNull { it.name == name } ?: return NewCategoryName.Ok(name)
        return if (same.isArchived) NewCategoryName.UsedBefore else NewCategoryName.Duplicate
    }

    const val NAME_DUPLICATE = "이미 있는 카테고리예요."
    const val NAME_USED_BEFORE = "예전에 사용했던 카테고리 이름이에요. 다른 이름으로 만들어 주세요."

    /** Delete confirmation copy (카테고리 관리). */
    fun deleteTitle(name: String) = "'$name' 카테고리를 삭제할까요?"
    const val DELETE_BODY = "기존 기록의 카테고리는 그대로 유지돼요.\n새 기록에서는 더 이상 선택할 수 없어요."
    const val DELETE_CANCEL = "취소"
    const val DELETE_CONFIRM = "삭제"
}

sealed interface NewCategoryName {
    data class Ok(val name: String) : NewCategoryName
    data object Blank : NewCategoryName
    /** An active category already has this name. */
    data object Duplicate : NewCategoryName
    /** A deleted (archived) category still holds this name. */
    data object UsedBefore : NewCategoryName
}
