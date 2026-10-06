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

    /** Delete confirmation copy (the category management UI itself is a later step). */
    fun deleteTitle(name: String) = "'$name' 카테고리를 삭제할까요?"
    const val DELETE_BODY = "기존 기록의 카테고리는 그대로 유지돼요.\n새 기록에서는 더 이상 선택할 수 없어요."
    const val DELETE_CANCEL = "취소"
    const val DELETE_CONFIRM = "삭제"
}
