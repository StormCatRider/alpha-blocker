// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * =============================================================================
 * Verses.kt — built-in Bible verses for the block screen
 *
 * English: Douay-Rheims (Challoner revision), the classic Catholic English
 * Bible. Spanish: Torres Amat (1825), the classic Catholic Spanish Bible
 * (see BUILT_IN_ES). Both are in the public domain, so they can be bundled.
 * The block screen uses the list matching the app's language.
 *
 * Psalm numbers follow the Douay-Rheims (Vulgate) numbering; the modern
 * number is given in brackets, e.g. "Psalm 50:12 [51:10]".
 *
 * PURITY verses appear when adult content or a non-allowed browser is blocked.
 * DILIGENCE verses appear when an app is blocked by a rule, schedule or limit.
 *
 * Wording was entered by hand: please check any verse against your own
 * Douay-Rheims copy and correct it here if needed. You can also add your own
 * verses (any translation) in the app's Verses tab.
 * =============================================================================
 */
package com.alphablocker.core

import com.alphablocker.data.Verse
import com.alphablocker.data.VerseTheme
import com.alphablocker.data.VerseTheme.DILIGENCE
import com.alphablocker.data.VerseTheme.PURITY
import kotlin.random.Random

object Verses {

    val BUILT_IN: List<Verse> = listOf(
        // ------------------------------------------------------------ PURITY
        v("p1", "1 Corinthians 10:13", "Let no temptation take hold on you, but such as is human. And God is faithful, who will not suffer you to be tempted above that which you are able: but will make also with temptation issue, that you may be able to bear it.", PURITY),
        v("p2", "Matthew 26:41", "Watch ye, and pray that ye enter not into temptation. The spirit indeed is willing, but the flesh weak.", PURITY),
        v("p3", "Psalm 50:12 [51:10]", "Create a clean heart in me, O God: and renew a right spirit within my bowels.", PURITY),
        v("p4", "James 4:7", "Be subject therefore to God, but resist the devil, and he will fly from you.", PURITY),
        v("p5", "2 Timothy 2:22", "But flee thou youthful desires, and pursue justice, faith, charity, and peace, with them that call on the Lord out of a pure heart.", PURITY),
        v("p6", "Philippians 4:13", "I can do all things in him who strengtheneth me.", PURITY),
        v("p7", "Matthew 5:8", "Blessed are the clean of heart: for they shall see God.", PURITY),
        v("p8", "1 Corinthians 6:18", "Fly fornication. Every sin that a man doth, is without the body; but he that committeth fornication, sinneth against his own body.", PURITY),
        v("p9", "1 Corinthians 6:19", "Or know you not, that your members are the temple of the Holy Ghost, who is in you, whom you have from God; and you are not your own?", PURITY),
        v("p10", "1 Thessalonians 4:3", "For this is the will of God, your sanctification; that you should abstain from fornication.", PURITY),
        v("p11", "Job 31:1", "I made a covenant with my eyes, that I would not so much as think upon a virgin.", PURITY),
        v("p12", "Psalm 118:37 [119:37]", "Turn away my eyes that they may not behold vanity: quicken me in thy way.", PURITY),
        v("p13", "Psalm 118:9 [119:9]", "By what doth a young man correct his way? by observing thy words.", PURITY),
        v("p14", "Matthew 5:28", "But I say to you, that whosoever shall look on a woman to lust after her, hath already committed adultery with her in his heart.", PURITY),
        v("p15", "Galatians 5:16", "I say then, walk in the spirit, and you shall not fulfil the lusts of the flesh.", PURITY),
        v("p16", "Romans 13:14", "But put ye on the Lord Jesus Christ, and make not provision for the flesh in its concupiscences.", PURITY),
        v("p17", "1 Peter 5:8", "Be sober and watch: because your adversary the devil, as a roaring lion, goeth about seeking whom he may devour.", PURITY),
        v("p18", "2 Corinthians 12:9", "My grace is sufficient for thee; for power is made perfect in infirmity.", PURITY),
        v("p19", "Hebrews 4:16", "Let us go therefore with confidence to the throne of grace: that we may obtain mercy, and find grace in seasonable aid.", PURITY),
        v("p20", "1 John 1:9", "If we confess our sins, he is faithful and just, to forgive us our sins, and to cleanse us from all iniquity.", PURITY),
        v("p21", "Philippians 4:8", "For the rest, brethren, whatsoever things are true, whatsoever modest, whatsoever just, whatsoever holy, whatsoever lovely, whatsoever of good fame, if there be any virtue, if any praise of discipline, think on these things.", PURITY),
        v("p22", "Romans 12:21", "Be not overcome by evil, but overcome evil by good.", PURITY),
        v("p23", "Psalm 22:1 [23:1]", "The Lord ruleth me: and I shall want nothing.", PURITY),
        v("p24", "Isaias 40:31", "But they that hope in the Lord shall renew their strength, they shall take wings as eagles, they shall run and not be weary, they shall walk and not faint.", PURITY),
        v("p25", "Matthew 11:28", "Come to me, all you that labour, and are burdened, and I will refresh you.", PURITY),
        v("p26", "Ephesians 6:11", "Put you on the armour of God, that you may be able to stand against the deceits of the devil.", PURITY),
        v("p27", "Psalm 26:1 [27:1]", "The Lord is my light and my salvation, whom shall I fear?", PURITY),
        v("p28", "Ecclesiasticus 18:30", "Go not after thy lusts, but turn away from thy own will.", PURITY),
        v("p29", "Romans 6:12", "Let not sin therefore reign in your mortal body, so as to obey the lusts thereof.", PURITY),
        v("p30", "Galatians 5:22-23", "But the fruit of the Spirit is, charity, joy, peace, patience, benignity, goodness, longanimity, mildness, faith, modesty, continency, chastity.", PURITY),

        // --------------------------------------------------------- DILIGENCE
        v("d1", "Proverbs 6:6", "Go to the ant, O sluggard, and consider her ways, and learn wisdom.", DILIGENCE),
        v("d2", "Proverbs 6:9", "How long wilt thou sleep, O sluggard? when wilt thou rise out of thy sleep?", DILIGENCE),
        v("d3", "Proverbs 10:4", "The slothful hand hath wrought poverty: but the hand of the diligent getteth riches.", DILIGENCE),
        v("d4", "Proverbs 12:24", "The hand of the valiant shall bear rule: but that which is slothful, shall be under tribute.", DILIGENCE),
        v("d5", "Proverbs 13:4", "The sluggard willeth and willeth not: but the soul of them that work, shall be made fat.", DILIGENCE),
        v("d6", "Proverbs 14:23", "In much work there shall be abundance: but where there are many words, there is oftentimes want.", DILIGENCE),
        v("d7", "Proverbs 16:3", "Lay open thy works to the Lord: and thy thoughts shall be directed.", DILIGENCE),
        v("d8", "Proverbs 19:15", "Slothfulness casteth into a deep sleep, and an idle soul shall suffer hunger.", DILIGENCE),
        v("d9", "Proverbs 20:13", "Love not sleep, lest poverty oppress thee: open thy eyes, and be filled with bread.", DILIGENCE),
        v("d10", "Proverbs 21:5", "The thoughts of the industrious always bring forth abundance: but every sluggard is always in want.", DILIGENCE),
        v("d11", "Ecclesiastes 9:10", "Whatsoever thy hand is able to do, do it earnestly.", DILIGENCE),
        v("d12", "Romans 12:11", "In carefulness not slothful. In spirit fervent. Serving the Lord.", DILIGENCE),
        v("d13", "Colossians 3:23", "Whatsoever you do, do it from the heart, as to the Lord, and not to men.", DILIGENCE),
        v("d14", "2 Thessalonians 3:10", "If any man will not work, neither let him eat.", DILIGENCE),
        v("d15", "Ephesians 5:15-16", "See therefore, brethren, how you walk circumspectly: not as unwise, but as wise: redeeming the time, because the days are evil.", DILIGENCE),
        v("d16", "1 Corinthians 15:58", "Therefore, my beloved brethren, be ye steadfast and unmoveable; always abounding in the work of the Lord, knowing that your labour is not in vain in the Lord.", DILIGENCE),
        v("d17", "Hebrews 6:12", "That you become not slothful, but followers of them, who through faith and patience shall inherit the promises.", DILIGENCE),
        v("d18", "Galatians 6:9", "And in doing good, let us not fail. For in due time we shall reap, not failing.", DILIGENCE),
        v("d19", "Colossians 4:5", "Walk with wisdom towards them that are without, redeeming the time.", DILIGENCE),
        v("d20", "Proverbs 31:27", "She hath looked well to the paths of her house, and hath not eaten her bread idle.", DILIGENCE),
    )


    /**
     * SPANISH built-in verses, shown when the app is in Spanish.
     *
     * Translation: Torres Amat (1825), the classic Catholic Spanish Bible
     * translated from the Vulgate; public domain. Text as published with
     * modern spelling on bibliatodo.com ("Biblia Torres Amat 1825").
     * Psalms use the Vulgate numbering with the modern number in brackets.
     *
     * Only verses whose Spanish text was checked to match the meaning of the
     * Douay-Rheims verse above are included (34 of 50); the online copy has
     * some verses filed under a neighbouring number, and those were left out
     * rather than risk a wrong reference. Please compare with your own Torres
     * Amat copy. Light edits: a leading "y me respondió:" / "sino" was
     * dropped, a closing comma became a period or question mark.
     */
    val BUILT_IN_ES: List<Verse> = listOf(
        // ------------------------------------------------------------ PUREZA
        v("es-p1", "1 Corintios 10:13", "Hasta ahora no habéis tenido sino tentaciones humanas, u ordinarias; pero fiel es Dios, que no permitirá seáis tentados sobre vuestras fuerzas, sino que de la misma tentación os hará sacar provecho para que podáis sosteneros.", PURITY),
        v("es-p2", "Mateo 26:41", "Velad y orad para no caer en la tentación. Que si bien el espíritu está pronto, la carne es flaca.", PURITY),
        v("es-p3", "Salmo 50:12 [51:10]", "Crea en mí, ¡oh Dios!, un corazón puro, y renueva en mis entrañas el espíritu de rectitud.", PURITY),
        v("es-p6", "Filipenses 4:13", "Todo lo puedo en aquel que me conforta, esto es, en Cristo.", PURITY),
        v("es-p7", "Mateo 5:8", "Bienaventurados los que tienen puro su corazón, porque ellos verán a Dios.", PURITY),
        v("es-p8", "1 Corintios 6:18", "Huid de la fornicación. Cualquier otro pecado que cometa el hombre, está fuera del cuerpo; pero el que fornica contra su cuerpo peca.", PURITY),
        v("es-p9", "1 Corintios 6:19", "¿Por ventura no sabéis que vuestros cuerpos son templos del Espíritu Santo, que habita en vosotros, el cual habéis recibido de Dios, y que ya no sois de vosotros?", PURITY),
        v("es-p10", "1 Tesalonicenses 4:3", "Esta es la voluntad de Dios, a saber, vuestra santificación, que os abstengáis de la fornicación.", PURITY),
        v("es-p11", "Job 31:1", "Desde joven hice pacto con mis ojos de no mirar, ni siquiera pensar con mal fin en una virgen.", PURITY),
        v("es-p13", "Salmo 118:9 [119:9]", "¿Cómo enmendará el tierno joven su conducta? Observando tus palabras o preceptos.", PURITY),
        v("es-p14", "Mateo 5:28", "Yo os digo más: Cualquiera que mirare a una mujer con mal deseo hacia ella, ya adulteró en su corazón.", PURITY),
        v("es-p16", "Romanos 13:14", "Mas revestíos de nuestro Señor Jesucristo, y no busquéis cómo contentar los antojos de vuestra sensualidad.", PURITY),
        v("es-p18", "2 Corintios 12:9", "Bástate mi gracia, porque el poder mío brilla y consigue su fin por medio de la flaqueza. Así que con gusto me gloriaré de mis flaquezas o enfermedades, para que haga morada en mí el poder de Cristo.", PURITY),
        v("es-p21", "Filipenses 4:8", "Por lo demás, hermanos míos, todo lo que es conforme a la verdad, todo lo que respira pureza, todo lo justo, todo lo que es santo, o santifica, todo lo que os haga amables, todo lo que sirve al buen nombre, toda virtud, toda disciplina loable, esto sea vuestro estudio.", PURITY),
        v("es-p22", "Romanos 12:21", "No te dejes vencer del mal, o del deseo de venganza, mas procura vencer al mal con el bien, o a fuerza de beneficios.", PURITY),
        v("es-p23", "Salmo 22:1 [23:1]", "El Señor es mi pastor, nada me faltará.", PURITY),
        v("es-p24", "Isaías 40:31", "Mas los que tienen puesta en el Señor su esperanza, adquirirán nuevas fuerzas, tomarán alas como de águila, correrán y no se fatigarán, andarán y no desfallecerán.", PURITY),
        v("es-p25", "Mateo 11:28", "Venid a mí todos los que andáis agobiados con trabajos y cargas, que yo os aliviaré.", PURITY),
        v("es-p26", "Efesios 6:11", "Revestíos de toda la armadura de Dios, para poder contrarrestar las acechanzas del diablo.", PURITY),
        v("es-p27", "Salmo 26:1 [27:1]", "El Señor es mi luz y mi salvación: ¿a quién he de temer yo? El Señor es el defensor de mi vida: ¿quién me hará temblar?", PURITY),

        // ------------------------------------------------------- DILIGENCIA
        v("es-d1", "Proverbios 6:6", "Anda, ¡oh perezoso!, mira la hormiga, y considera su obrar, y aprende a ser sabio.", DILIGENCE),
        v("es-d2", "Proverbios 6:9", "¿Hasta cuándo has de dormir tú, oh perezoso? ¿Cuándo despertarás de tu sueño?", DILIGENCE),
        v("es-d3", "Proverbios 10:4", "La mano desidiosa produce la mendicidad; pero la mano activa acumula riquezas.", DILIGENCE),
        v("es-d4", "Proverbios 12:24", "La mano de los fuertes dominará; pero la mano perezosa será tributaria.", DILIGENCE),
        v("es-d5", "Proverbios 13:4", "El perezoso quiere y no quiere; mas las personas laboriosas se llenarán de bienes.", DILIGENCE),
        v("es-d6", "Proverbios 14:23", "De toda ocupación se saca provecho; pero del mucho hablar, sólo miseria.", DILIGENCE),
        v("es-d7", "Proverbios 16:3", "Dirige hacia el Señor tus obras; y tendrán buen éxito tus designios.", DILIGENCE),
        v("es-d9", "Proverbios 20:13", "No seas amigo del sueño, para que no te veas oprimido en la indigencia; abre tus ojos, desvélate, y te sobrará pan.", DILIGENCE),
        v("es-d10", "Proverbios 21:5", "Los pensamientos del hombre activo y diligente siempre traen la abundancia; pero todos los perezosos viven siempre en miseria.", DILIGENCE),
        v("es-d11", "Eclesiastés 9:10", "Todo cuanto pudieres hacer de bueno, hazlo sin perder tiempo; puesto que ni obra, ni pensamiento, ni sabiduría, ni ciencia ha lugar en el sepulcro, hacia el cual vas corriendo.", DILIGENCE),
        v("es-d13", "Colosenses 3:23", "Todo lo que hagáis, hacedlo de buena gana, como quien sirve a Dios y no a hombres.", DILIGENCE),
        v("es-d15", "Efesios 5:16", "Como prudentes, recobrando en cierto modo el tiempo perdido, porque los días de nuestra vida son malos.", DILIGENCE),
        v("es-d16", "1 Corintios 15:58", "Así que, amados hermanos míos, estad firmes y constantes, trabajando siempre más y más en la obra del Señor, que sabéis que vuestro trabajo no quedará sin recompensa delante del Señor.", DILIGENCE),
        v("es-d18", "Gálatas 6:9", "No nos cansemos, pues, de hacer bien; porque si perseveramos, a su tiempo recogeremos el fruto.", DILIGENCE),
    )

    /** Built-in verses in the app's current language. */
    fun builtIn(): List<Verse> = if (I18n.spanish) BUILT_IN_ES else BUILT_IN

    /** Name of the translation of [verse], or null for your own verses. */
    fun translationOf(verse: Verse): String? = when (verse) {
        in BUILT_IN -> "Douay-Rheims"
        in BUILT_IN_ES -> "Torres Amat"
        else -> null
    }

    /**
     * Picks a random verse of [theme] from the built-in verses in the current
     * language plus your own verses (shown as you typed them).
     */
    fun random(theme: VerseTheme, custom: List<Verse>): Verse {
        val pool = (builtIn() + custom).filter { it.theme == theme }
        return if (pool.isEmpty()) builtIn().first() else pool[Random.nextInt(pool.size)]
    }

    private fun v(id: String, ref: String, text: String, theme: VerseTheme) = Verse(id, ref, text, theme)
}
