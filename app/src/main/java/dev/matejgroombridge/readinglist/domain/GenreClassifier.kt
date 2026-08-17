package dev.matejgroombridge.readinglist.domain

import dev.matejgroombridge.readinglist.data.model.Genres

/**
 * Maps Open Library's free-form subject tags onto the curated [Genres]
 * catalogue, so a book lands on the right shelf section the moment it's added
 * and the user never has to file anything by hand.
 *
 * Why this is rule-based rather than "take the first subject": Open Library
 * subjects are crowd-maintained and extremely noisy. A typical work carries a
 * mix of real genre tags, plot nouns, and cataloguing debris:
 *
 *     ["Fiction", "Science fiction", "Desert ecology", "Space colonies",
 *      "Dune (Imaginary place)", "Nebula Award Winner", "Large type books"]
 *
 * Picking the first entry gives "Fiction"; picking the most common gives
 * noise. Instead every subject is scored against a keyword table and the
 * highest-scoring genre wins.
 *
 * Two properties keep the results sane:
 *
 *  1. **Specificity beats generality.** Overlapping rules are weighted so the
 *     narrower phrase wins: `science fiction` (10) outranks `science` (3),
 *     `true crime` (10) outranks `crime` (3), `historical fiction` (10)
 *     outranks `history` (4). This is why the weights are hand-tuned rather
 *     than uniform.
 *  2. **Earlier subjects count for more.** Open Library roughly orders
 *     subjects by prominence, so score decays with position — enough to break
 *     ties in favour of the leading tags, not enough for one stray first entry
 *     to overrule a clear consensus further down.
 */
object GenreClassifier {

    /** One keyword → genre mapping. [weight] encodes how specific the keyword is. */
    private data class Rule(val genreKey: String, val keyword: String, val weight: Double)

    /**
     * Builds the rule table. Keywords are matched on whole-word boundaries
     * (see [normalise]), so `art` matches "Art history" but not "Heart" or
     * "Started" — the reason this isn't a plain `contains` check.
     */
    private val rules: List<Rule> = buildList {
        fun add(genreKey: String, weight: Double, vararg keywords: String) {
            keywords.forEach { add(Rule(genreKey, it, weight)) }
        }

        // ── Explicit shelf labels (weight 20) ────────────────────
        // Publisher/BISAC-style category names: tags that state outright
        // which shelf a book belongs on, as opposed to describing what it's
        // about. They're weighted to be decisive because Open Library often
        // pairs one of them with several topical qualifiers that would
        // otherwise outvote it — a habits book carries a single "Self-Help"
        // alongside three "(Psychology)" tags, and Self-Help is the answer.
        add("self-improvement", 20.0, "self help", "selfhelp", "personal development")
        add("true-crime", 20.0, "true crime")

        // ── Highly specific compound phrases (weight 10) ─────────
        // These exist to beat the generic single words further down.
        add("sci-fi", 10.0, "science fiction", "sciencefiction", "space opera", "dystopian", "cyberpunk")
        add("historical-fiction", 10.0, "historical fiction", "historical novels")
        add("comics", 10.0, "graphic novel", "graphic novels", "comic book", "comic books", "manga")
        add("mystery-thriller", 10.0, "detective and mystery stories", "spy stories")
        add("biography", 10.0, "autobiography", "personal memoirs")

        // ── Genre words (weight 5–8) ─────────────────────────────
        add("sci-fi", 6.0, "dystopia", "time travel", "space flight", "extraterrestrial")
        add("fantasy", 8.0, "fantasy", "epic fantasy", "magic", "wizards", "dragons", "mythology")
        add("mystery-thriller", 7.0, "mystery", "thriller", "detective", "suspense", "noir", "espionage")
        add("horror", 8.0, "horror", "ghost stories", "vampires", "supernatural", "zombies")
        add("romance", 8.0, "romance", "love stories", "romantic")
        add("poetry", 8.0, "poetry", "poems", "verse")
        add("comics", 7.0, "comics", "cartoons", "superhero", "superheroes")
        add("classics", 6.0, "classical literature", "classic literature")
        add("biography", 7.0, "biography", "memoir", "memoirs", "biographies", "correspondence", "diaries")

        add("history", 6.0, "history", "historical", "ancient", "medieval", "civilization", "archaeology")
        add("science", 6.0, "physics", "biology", "chemistry", "astronomy", "evolution", "genetics", "neuroscience", "mathematics", "cosmology")
        add("science", 4.0, "science", "nature", "natural history", "environment", "climate", "ecology", "animals")
        add("technology", 7.0, "computers", "computer science", "programming", "software", "artificial intelligence", "internet", "technology", "engineering", "data processing")
        add("philosophy", 7.0, "philosophy", "ethics", "metaphysics", "logic", "stoicism", "existentialism")
        add("psychology", 7.0, "psychology", "psychological", "cognition", "behavior", "behaviour", "consciousness", "mental health")
        add("self-improvement", 7.0, "success", "motivation", "productivity", "habit", "habits", "mindfulness", "conduct of life", "self actualization")
        add("business", 7.0, "business", "economics", "management", "entrepreneurship", "marketing", "finance", "investing", "leadership", "money")
        add("politics", 6.0, "political science", "politics", "government", "sociology", "social science", "feminism", "race relations", "war", "law", "journalism")
        add("health", 7.0, "health", "fitness", "nutrition", "diet", "exercise", "medicine", "wellness")
        add("health", 6.0, "anatomy", "physiology", "cardiovascular", "human body", "surgery", "disease")
        add("religion", 7.0, "religion", "spirituality", "christianity", "islam", "buddhism", "judaism", "theology", "bible", "meditation")
        add("art", 7.0, "art", "design", "architecture", "photography", "painting", "music", "film", "cinema", "drawing")
        add("travel", 7.0, "travel", "voyages and travels", "description and travel", "adventure")
        add("cooking", 8.0, "cooking", "cookbooks", "cookery", "recipes", "food", "baking")

        // ── Weak fiction markers (weight 1.5–3) ──────────────────
        // A book tagged only "Fiction" should still land somewhere better
        // than Unsorted, but any real genre tag must be able to outvote it.
        add("literary-fiction", 3.0, "literary", "literary fiction")
        add("literary-fiction", 1.5, "fiction", "novel", "novels", "fiction general", "american fiction", "english fiction")
        add("classics", 2.5, "classics", "classic")
        add("mystery-thriller", 3.0, "crime", "murder")
        add("history", 2.0, "biography history", "world war")
    }

    /** Rules bucketed by keyword so scoring is a map lookup per token span. */
    private val rulesByKeyword: Map<String, List<Rule>> = rules.groupBy { it.keyword }

    /** Longest keyword in the table, in words — bounds the n-gram scan. */
    private val maxKeywordWords: Int = rulesByKeyword.keys.maxOf { it.split(' ').size }

    /**
     * Returns the best-matching genre key for [subjects], or
     * [Genres.UNSORTED_KEY] when nothing scores above [MIN_SCORE].
     *
     * Landing in Unsorted is a deliberate outcome rather than a failure: a
     * visible "Unsorted" section the user can re-file from is far better than
     * silently guessing wrong and burying the book under a heading they'd
     * never think to look under.
     */
    fun classify(subjects: List<String>): String {
        if (subjects.isEmpty()) return Genres.UNSORTED_KEY

        val scores = mutableMapOf<String, Double>()
        subjects.forEachIndexed { index, subject ->
            // Mild positional decay: the 1st subject counts ~1.0, the 20th
            // ~0.4. Enough to break ties, not enough to let one stray leading
            // tag overrule a consensus further down the list.
            val positional = 1.0 / (1.0 + index * 0.08)
            scoreSubject(subject).forEach { (genreKey, weight) ->
                scores[genreKey] = (scores[genreKey] ?: 0.0) + weight * positional
            }
        }

        val best = scores.maxByOrNull { it.value } ?: return Genres.UNSORTED_KEY
        return if (best.value >= MIN_SCORE) best.key else Genres.UNSORTED_KEY
    }

    /**
     * Scores a single subject string, returning each genre it implies and the
     * weight it contributes.
     *
     * Every 1..[maxKeywordWords] word span is looked up, so multi-word
     * keywords match without a regex pass per rule. A subject that hits
     * several rules for the same genre keeps only the strongest — otherwise
     * "Science fiction" would double-count via both `science fiction` and
     * `fiction` and drown out a competing tag.
     */
    private fun scoreSubject(subject: String): Map<String, Double> {
        val words = normalise(subject).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyMap()

        val hits = mutableMapOf<String, Double>()
        for (start in words.indices) {
            val maxSpan = minOf(maxKeywordWords, words.size - start)
            for (span in 1..maxSpan) {
                val phrase = words.subList(start, start + span).joinToString(" ")
                rulesByKeyword[phrase]?.forEach { rule ->
                    val existing = hits[rule.genreKey] ?: 0.0
                    if (rule.weight > existing) hits[rule.genreKey] = rule.weight
                }
            }
        }
        return hits
    }

    /**
     * Lowercases and strips punctuation so subject strings reduce to plain
     * space-separated words. "Science-fiction, American." and
     * "Science fiction (American)" both become "science fiction american",
     * which is what makes whole-word keyword lookup reliable.
     */
    private fun normalise(subject: String): String =
        subject.lowercase().replace(NON_WORD, " ").trim()

    private val NON_WORD = Regex("[^a-z0-9]+")

    /**
     * Floor for accepting a classification. Roughly "one weak fiction marker
     * at full positional weight" — below this the evidence is a single
     * incidental tag and Unsorted is the honest answer.
     */
    private const val MIN_SCORE = 1.4
}
