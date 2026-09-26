package me.regadpole.plumbot.filter

object FilterManagerHolder {
    @Volatile
    var manager: FilterThesaurusManager? = null
}
