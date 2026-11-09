# Architecture & choix techniques

> Document de justification des choix d'architecture, des patterns et des librairies
> appliqués (exigence de l'énoncé)

Application : **AndroidRecruitmentTestApp** : liste d'albums (photos), favoris, écran de détail, persistance offline.

Stack : Kotlin 2.4, Jetpack Compose, AGP 9.5 / Gradle 9.8, module `:app` +
module `:data`.

---

## Sommaire

1. [Injection de dépendances : Hilt](#1-injection-de-dépendances--hilt)
2. [Architecture par couches (MVVM + Repository)](#2-architecture-par-couches)
3. [Persistance offline : Room](#3-persistance-offline--room)
4. [Navigation](#4-navigation)
5. [Réseau : Retrofit + kotlinx-serialization](#5-réseau)
6. [UI : Compose + Spark design system](#6-ui)
7. [Tests](#7-tests)
8. [Bugs trouvés & corrigés](#8-bugs-trouvés--corrigés)
9. [Planification des évolutions](#9-planification-des-évolutions)

---

## 1. Injection de dépendances : Hilt

### 1.1 Pourquoi un framework d'injection (le constat de départ)

Le projet d'origine n'avait **aucun framework** : la DI était manuelle, portée par un
`Application` qui implémentait une interface maison (`AppDependenciesProvider`) et
exposait un sac d'objets (`AppDependencies`) où chaque dépendance était résolue par
`by lazy` au moment du premier accès, puis descendue à la main :
`activity → application → AppDependencies → DataDependencies → AlbumRepository`.

Cette approche fonctionne à l'échelle de 3 classes, mais elle casse dès qu'elle
grandit :

| Problème de la DI manuelle | Symptôme visible dans le code d'origine |
|---|---|
| **Câblage manuel dupliqué** | `MainActivity` et `DetailsActivity` récupéraient chacun `dependencies…` et recopiaient le chemin d'acquisition |
| **Aucun contrôle de portée** | `AnalyticsHelper` était créé en singleton "de facto" mais `initialize(context)` recevait un Context d'**Activity**, stocké dans un objet vivant avec l'Application → **fuite mémoire** (LeakCanary) |
| **Initialisation ordonnée à la main** | `AnalyticsHelper` devait être `initialize()`-é *avant usage* dans chaque écran, sinon silent no-op (le helper ignorait tout tracking si non initialisé) |
| **Testabilité** | Pour construire un `AlbumsViewModel` il fallait recréer toute la chaîne à la main dans le test |
| **Coûts d'incrémentation** | Ajouter une dépendance = naviguer dans tout le graphe manuel : exactement le genre de dette qu'une code review rigoureuse finit par interdire |

### 1.2 Pourquoi **Hilt** spécifiquement

Le choix a été fait parmi : DI manuelle (statu quo), Koin, Dagger 2 nu, Hilt.

**Hilt = Dagger 2 au-dessus d'une API standardisée Android.** Ce qui l'a emporté :

1. **Sécurité à la compilation.** Le graphe complet (et si chaque dépendance est
   fournie, au bon scope, dans le bon composant) est vérifié par le processeur
   d'annotations. Un lien manquant = **erreur de build**, jamais un crash à
   l'exécution — contrairement à Koin (résolution à l'exécution), où un binding
   oublié ne se voit qu'au lancement de l'app. Pour le critère d'énoncé
   *« l'application ne doit pas crasher systématiquement »*, une erreur à la
   compilation plutôt qu'à l'exécution est le meilleur pari.
2. **Dagger lui-même est ce qui est derrière la majorité des apps professionnelles**
   du marché Android — Google écrit son guide de référence autour de Hilt. Le
   transfert de compétences est direct pour toute équipe Android.
3. **Intégration Android-native** que Dagger nu n'apporte pas :
   `@HiltAndroidApp`, `@AndroidEntryPoint` pour les Activities/Fragments,
   `@HiltViewModel` compatible avec `viewModelScope` + `SavedStateHandle`
   automatique, `@ApplicationContext` typé qui rend la fuite de Context
   *structurellement impossible* (le qualifier porte le bon contexte).
4. **KSP (pas kapt)** : Hilt 2.60+ supporte officiellement KSP stable, qui est
   déjà dans ce projet pour la compilation (2.3.10) — pas de kapt, pas
   d'incompatibilité de build, et les générations sont nettement plus rapides.
5. **Koin** reste un bon choix sur des projets légers, mais ici il aurait exigé de
   tenir le graphe "à la main" en Kotlin, sans vérification compile-time ; à
   l'inverse, le boilerplate Hilt est quasi nul au regard des 4 bindings voulus
   (`Json`, `OkHttpClient`, `Retrofit`, `AlbumApiService`).

### 1.3 Comment c'est connecté

Tout vit dans le composant **`SingletonComponent`** (portée app-wide) :
`@HiltAndroidApp` sur `PhotoApp` installe le graphe, `:data` expose son module
réseau `DataModule` (`@Module @InstallIn(SingletonComponent)`) qui fournit
`Json`, `OkHttpClient` (log BODY en debug only), `Retrofit` et
`AlbumApiService`. `AlbumRepository` et `AnalyticsHelper` sont, eux, résolus
par **injection de constructeur** (`@Inject constructor`) : pas besoin de
`@Provides` "à la main", Hilt construit la classe dès que toutes ses
dépendances sont dans le graphe — la notation standard pour ce genre d'objet.
La description détaillée du module `:data` figure au §2 (Architecture par
couches).

#### Côté consommateurs

- **Activities** → `@AndroidEntryPoint` ajoute le code de bytecode par plugin
  Gradle pour que la field injection se fasse proprement (avant `onCreate`) :

  ```kotlin
  @AndroidEntryPoint
  class MainActivity : ComponentActivity() {
      @Inject lateinit var analyticsHelper: AnalyticsHelper
  }
  ```

- **ViewModels** → `@HiltViewModel` + `viewModels()` : plus aucune
  `ViewModelProvider.Factory` à écrire, Hilt fournit à la fois le
  graphe et le store de ViewModels dans le bon scope :

  ```kotlin
  @HiltViewModel
  class AlbumsViewModel @Inject constructor(
      private val repository: AlbumRepository,
  ) : ViewModel()
  ```

- **Module library**: `:data` expose son graphe via annotations **sans appliquer
  le plugin Gradle Hilt** (réservé au transform de bytecode pour les
  `@AndroidEntryPoint`, qui ne concerne pas une library). Seuls la dépendance
  d'annotations (`hilt-android`) et le `ksp(hilt-compiler)` suffisent.

### 1.4 Conséquences concrètes (avant / après)

| | Avant (manual DI) | Après (Hilt) |
|---|---|---|
| Fuite de Context | `AnalyticsHelper.initialize(activity)` stockait l'Activity dans un objet singleton "de facto" | `@ApplicationContext` injecté : le type garantit un Context app, plus aucun appel à `initialize()` n'existe |
| Initialisation à la main | `initialize()` requis dans chaque Activity avant usage, no-op silencieux sinon | `AnalyticsHelper` injecté déjà prêt à l'emploi |
| Factory ViewModel | `AlbumsViewModel.Factory` maison + `ViewModelProvider(this, factory)` | `by viewModels()`, sans factory |
| Ajouter une dépendance | Ajouter au sac `AppDependencies` + traverser chaque Activity | Ajouter le binding (souvent juste `@Inject constructor`) et l'injecter |
| Vérification | Aucune — erreurs possibles à l'exécution | Erreurs découvertes par le compilateur (le graphe complet est vérifié) |
| Test unitaire VM | Chaîne recréée à la main dans le test | Le constructeur reste une fonction ordinaire, `AlbumsViewModel(repository)` : test unitaire inchangé, non attaché à Hilt |

### 1.5 Ce que ce choix *ne* fait pas (franchise sur les limites)

- Hilt impose des traitements de bytecode (plugin Gradle) et une
  structure par composants, plus rigide que Koin sur des projets très petits.
- Les ViewModels `@HiltViewModel` restent **testables en test unitaire JVM pur
  comme prévu** (on les construit à la main avec leurs arguments); en contrepartie
  il y aura, plus tard, des tests **Robolectric/instrumentés** si l'on veut tester
  le graphe lui-même.

---

## 2. Architecture par couches (MVVM + Repository)

### 2.1 La carte

```
┌─────────────────── app : application module ───────────────┐
│  racine = le shell : MainActivity (NavHost, barres),       │
│                     Routes, PhotoApp, analytics            │
│                                                            │
│  feature/albums    AlbumsScreen + AlbumsViewModel          │
│                    (+ AlbumsUiState dérivé)                │
│  feature/favorites FavoritesScreen + FavoritesViewModel    │
│  feature/detail    DetailsScreen + DetailsViewModel        │
│                                                            │
│  ui/               composables PARTAGÉS entre features :   │
│                     AlbumItem (les 2 onglets),             │
│                     FavoriteButton (liste + détail),       │
│                     shared elements, theme/                │
└──────────────────────┬─────────────────────────────────────┘
                       │ Album, AlbumRepository   (SEULE surface exposée)
┌──────────────────────▼───────── data : Android library ───┐
│  repository/ (AlbumRepository — offline-first)             │
│  network/ (Retrofit service + DTO)      database/ (Room)   │
│  mapper/ (DTO↔Entity↔domain)            di/ (graph Hilt)   │
│  model/ (Album — modèle domaine, 1 champ `favorite`)       │
└────────────────────────────────────────────────────────────┘
```

Chaque feature package est **cohésif par destination** : l'écran, son
ViewModel et son état UI y vivent côte à côte — lire une fonctionnalité ne
demande pas de sauter d'un dossier `viewmodel/` à un dossier `ui/`, et en
extraire un module Gradle plus tard sera un déplacement, pas une
réécriture. Les tests JVM miroient cette arborescence
(`test/.../feature/albums/AlbumsViewModelTest` etc.), et `Fakes.kt` reste
au package racine des tests car il est partagé par les trois suites.

### 2.2 Les règles de dépendance

- `:data` **ne connaît ni Compose, ni UI** : aucune dépendance UI dans son
  classpath (vérifiable dans `data/build.gradle.kts` : Retrofit, OkHttp,
  Room, Hilt, kotlinx-serialization seulement).
- `:app` **ne touche que deux types de `:data`** (`Album`,
  `AlbumRepository` — plus les mappers en tests). Ni `AlbumDao`, ni
  `AlbumEntity`, ni `AlbumDto` ne fuient dans la couche UI : impossible
  d'écrire une requête SQL ou un parse JSON depuis un ViewModel par
  accident (assuré par `implementation` et non `api` dans le catalog).
- Sens unique : `:app` → `:data` ; `:data` → Retrofit/Room/Hilt. Les events
  montent (ViewModel appelle `AlbumRepository.toggleFavorite`), le state
  descend (StateFlow collecté à l'écran) — UDF.

### 2.3 Pourquoi PAS davantage de modules (pour l'instant)

Deux modules suffisent ici, et ce n'est pas de la flemme : chaque module
doit être payé par un de ces critères :

| Critère de rentabilité d'un module | Ici ? |
|---|---|
| Un 2e consumer réutilisable (2e app, wear) | non |
| Frontières d'équipe (une module par feature team) | non |
| Isolation de compilation pour de gros lots d'écrans (build times) | 6 écrans/composants |
| Module JVM pur pour la testabilité du domaine | domaine = 1 data class + mappers |
| Dynamic features / allègement Play Store | aucun |

Le chemin d'évolution suit ce fil : **les features-first packages sont déjà
en place** (`feature/albums`, `feature/favorites`, `feature/detail` — cf.
§2.1) ; le saut suivant est le module Gradle, le jour où une de ces raisons
existera vraiment. Découper les ViewModels dans un package `viewmodel/`
(layer-first) aurait voulu l'inverse : un ViewModel séparé de son écran et
de son état UI, chaque nouvelle feature éclatée sur 3 dossiers.


---

## 3. Persistance offline : Room

### 3.1 Pourquoi Room (le constat de départ)

Le projet d'origine n'avait **aucune persistance** : les albums n'existaient
que dans l'état mémoire du ViewModel, donc vides à chaque redémarrage. Or
l'énoncé impose deux exigences qui vont dans le même sens : les données
doivent rester disponibles **hors ligne, même après redémarrage**, et les
favoris doivent être **persistés localement**. Une seule brique couvre les
deux : une base de données structurée.

Options considérées :

| Option | Verdict |
|---|---|
| **SharedPreferences** | Clé/valeur pur : sérialiser la liste en JSON dans une string, pas de requêtes, pas de réactivité, pas de migration. Acceptable pour *simuler* l'analytics (ce que fait `AnalyticsHelper`), pas pour un dataset interrogé |
| **DataStore** | Persiste bien un dataset sérialisé, mais pas de requêtes structurées ni de relations entre données ; adapté aux préférences, pas à une liste consultable |
| **SQLDelight** | Excellent (SQL type-safe first), mais moins standard côté Android/Google et moins accessible à la relecture qu'un choix mainstream |
| **Room** ✅ | Standard Android : requêtes SQL **vérifiées à la compilation**, `Flow` réactif (la liste se re-émet toute seule à chaque toggle de favori, sans code de resynchronisation manuel), migrations outillées, KSP déjà en place |

Les points décisifs pour ce projet :

1. **Room installe la base comme source de vérité** : la liste affichée vient
   de la base, le réseau se contente de la rafraîchir. Le mode "hors ligne"
   en devient naturel : si l'API est down mais que le cache existe, l'UI
   affiche les données, sans branche de code spéciale.
2. **Albums *et* favoris exigent des requêtes structurées** (jointure de la
   liste avec l'état favori), pas une liste sérialisée en blob.
3. **Le versionning du schéma est outillé** (`@Database(version = 1)`), utile
   dès la première évolution (§9).

### 3.2 Le schéma

Deux tables, reliées par le champ `albumId` :

```
┌─ albums ─────────────────────────────┐    ┌─ favorites ─────────────┐
│ id (PK)     ← id de la photo (JSON)  │    │ albumId (PK)            │
│ albumId     ← id de l'album "parent" │◀───┤                         │
│ title                                │    └── une ligne par favori ─┘
│ url                                  │
│ thumbnailUrl                         │
└──────────────────────────────────────┘
```

**Pourquoi une `favorites` séparée plutôt qu'une colonne `favorite` sur
`albums`** : le refresh réseau écrit les albums avec
`@Insert(onConflict = REPLACE)` — avec la colonne `favorite` sur la même
table, chaque refresh réseau **réinitialiserait** les favoris de
l'utilisateur. En séparant, le REPLACE sur `albums` ne touche jamais les
favoris : les toggles de l'utilisateur ne sont jamais perdus.

### 3.3 Le wiring

- `AlbumDao.observeAllWithFavorite()` : un `Flow<List<AlbumWithFavorite>>`
  dont la requête SQL calcule le flag directement dans SQLite
  (`EXISTS(SELECT 1 FROM favorites …)`) — pas de re-traitement Kotlin côté
  applicatif, et la table est re-notifiée automatiquement pour toute écriture
  sur `albums` ou `favorites`.
- `AlbumRepository` devient **offline-first** :
  - `observeAlbums(): Flow<List<Album>>` — lecture Room seule, c'est la source
    de vérité consommée par le ViewModel ;
  - `refresh()` — réseau puis cache (write-through) ; les erreurs remontent au
    ViewModel, qui décide de leur traitement ;
  - `toggleFavorite(albumId, favorite)` — écriture sur la table `favorites`.
- `AlbumsViewModel` : le `uiState` est déduit de
  `combine(observeAlbums(), refreshFailed)` — les albums s'affichent dès que
  la base en contient, et Error n'est montré que si **à la fois** le cache est
  vide et le réseau a échoué (dégradation = d'abord le cache).
- Hilt : `DatabaseModule` fournit `AppDatabase` et `AlbumDao`, toujours dans
  `SingletonComponent`.

### 3.4 Modèle domaine

Ce chantier a été l'occasion d'arrêter de faire circuler le DTO réseau
(`AlbumDto`) jusque dans l'UI : le ViewModel expose désormais des
`fr.leboncoin.data.model.Album`. Chaîne : `AlbumDto` (réseau) →
`AlbumEntity` (cache) → `Album` (UI), un mapper par maillon
(`AlbumMappers.kt`). Bénéfices : l'UI ne dépend plus du format JSON, et le
champ `favorite` — qui n'existe pas côté réseau — a une place naturelle.

### 3.5 Limites assumées

- `exportSchema = false` (fichier de schéma Room non exporté) : acceptable sur
  ce projet, mais une vraie app exporterait le schéma et aurait des tests de
  migration.
- Pas de tests Room in-memory en JVM pur (nécessite un environnement
  Android) : la sémantique REPLACE est répliquée par le fake hand-written
  `FakeAlbumDao` dans les tests unitaires ; un `androidTest` sur le DAO réel
  viendra avec la phase tests.
- Le DAO reste minimal (4 méthodes) : le filtrage et la pagination par album
  sont listés en évolutions (§9).

---

## 4. Navigation (écran de détail)

### 4.1 Détail : l'id navigue, pas l'objet

La navigation liste → détail transmet **uniquement l'id de l'album**
(`DetailsActivity.ALBUM_ID_KEY`, extra d'Intent). L'écran de détail n'affiche
pas d'objet reçu au lancement : il **ré-observe l'album depuis le repository**
(`AlbumRepository.observeAlbum(id)`), qui lit la base Room.

**Pourquoi pas un `Album @Parcelable` passé en extra** (l'approche naïve et
courante) :

| | Objet Parcelable en extra | Id + ré-observation (retenu) |
|---|---|---|
| Fraîcheur des données | L'objet est une copie figée au clic ; si le favori est toggled entre-temps, le détail affiche un état déphasé | Toujours l'état courant du cache (Room) |
| Changements de configuration | L'extra survit à la rotation (pas de crash), mais l'objet reste figé | Survit aussi (l'extra est conservé), et l'état est récupéré à chaud |
| **Process death** (éliminatoire) | L'extra d'Intent est restauré par le système — l'objet survit | L'id est restauré via `SavedStateHandle`, contenu rechargé depuis la base |
| Couplage | Le modèle domaine deviendrait `android.os.Parcelable` (+ plugin `kotlin-parcelize` dans `:data`, ce qui casse la neutralité Android de la couche domaine) | Modèle domaine pur ; la base reste la seule source de vérité |
| Extension Navigation Compose | Re-sérialiser l'objet pour un argument de route | Le pattern est déjà correct : l'argument de route EST un id (fait en §4.3) |

En pratique : `DetailsViewModel` lit l'id dans
`SavedStateHandle[ALBUM_ID_KEY]` (injecté automatiquement par Hilt dans le
constructeur du `@HiltViewModel`), expose
`album: StateFlow<Album?>` (null = inconnu → fallback), et `toggleFavorite()`
écrit sur la table favorites comme pour la liste. Un album n'existant pas
(ou cache vide avec réseau coupé) affiche la figure "work in progress"
plutôt qu'un crash — cohérent avec le critère « l'application ne doit pas
crasher systématiquement ».

### 4.2 Conception d'écran

Le bouton cœur (`FavoriteButton`) est **partagé** entre la liste et le détail
(`ui/FavoriteButton.kt`) : même interaction, mêmes strings d'accessibilité,
un seul endroit à faire évoluer. Les écrans sont stateless et préviewables,
testables sans activity.

### 4.3 Single-activity + Navigation Compose

L'application est passée à **une seule Activity + Navigation Compose** :

- `MainActivity` héberge un `NavHost` avec trois destinations : `albums`,
  `favorites` et `album/{albumId}` (objet `Routes`). `DetailsActivity` a
  disparu — l'argument de route `albumId: Int` (`NavType.IntType`) remplace
  l'extra d'Intent, la migration a **conservé la clé SavedStateHandle** :
  `DetailsViewModel` ne change pas d'une ligne, preuve que couche de routage
  et couche d'état sont découplées.
- Les ViewModels sont scopés **par destination** (`androidx.hilt:
  hilt-navigation-compose` → `hiltViewModel()` dans le composable) au lieu
  d'être activity-scoped, et la navigation entre onglets utilise
  `popUpTo(inclusive) + saveState/restoreState + launchSingleTop` : la pile
  ne porte que l'onglet courant (Back sur un onglet quitte l'app au lieu de
  rebondir vers l'autre onglet), et changer d'onglet conserve son état
  (défilement, favoris) sans dupliquer les VM.
- **Une seule app bar visible partout** : la top bar du shell suit la
  destination (titre "Albums"/"Favorites"/"Album details") et porte la
  flèche Back + le cœur favori sur le détail (état lu dans le
  `DetailsViewModel` du même back-stack entry). Pas de masquage/apparition
  de barre à l'ouverture du détail, et une seule barre… donc pas de double
  app bar. La bottom bar (Spark `NavigationBar`, items "Albums" /
  "Favorites") n'apparaît que sur les destinations top-level. Le
  `PaddingValues` du shell est appliqué à chaque écran de contenu — les
  insets ne sont consommés qu'une fois (pas de double marge statut-bar).
- **Transitions** : fondu entre onglets, glissement latéral vers/depuis le
  détail, et **shared element** sur l'image (thumbnail → image du détail,
  `SharedTransitionLayout` + CompositionLocals). La clé inclut l'onglet
  source (`albums-42` vs `favorites-42`, argument `?src=`) pour qu'aucun
  doublon ne matche pendant les transitions entre onglets.
- `FavoritesViewModel` observe la variante "favoris uniquement" de la query
  Room (`observeFavorites()` : inner join sur `favorites`) : togglée depuis
  la liste ou le détail, la rupture de favori se propage instantanément à
  l'onglet ; un état vide (rien de favori) est un état valide, couvert par
  une vue vide dédiée.
- Analytics : `trackScreenView` suit désormais la destination courante via
  `LaunchedEffect(route)` en un seul endroit, là où les deux Activities le
  faisaient chacune dans `onCreate`.

### 4.4 Rotation, changements de configuration, process death

*(critère éliminatoire de l'énoncé)*

- **Aucun `configChanges` ni `screenOrientation` dans le manifest** : on
  laisse le système recréer l'Activity, aucune "astuce" de maintien d'UI.
- **Les ViewModels survivent à la rotation** : ils vivent dans le
  `ViewModelStore` de leur `NavBackStackEntry` (navigation) / Activity, pas
  dans la composition. Le flag `refreshFailed` et l'état des flux
  (`stateIn(WhileSubscribed(5s))`) sont donc conservés : pas de re-fetch au
  simple fait de tourner l'écran.
- **Le contenu UI suit le viewport** : rien n'est codé "portrait" — les
  listes sont des `LazyColumn`, l'app bar et la bottom bar sont pilotées par
  les insets, et l'image du détail est bornée (`widthIn(max)`/`heightIn(max)`
  = 350, letterboxée en `Fit`) pour ne jamais occuper l'intégralité d'un
  viewport paysage plus bas.
- **L'état d'UI détaillé** (position de scroll des listes, items clés par
  id) est restauré par Compose via `rememberSaveable`/le registre de
  sauvegarde de la navigation.
- **Process death** : l'id de l'album du détail est lu dans
  `SavedStateHandle` (§4.1) et les arguments de navigation sont persistés
  par le NavController ; si le processus est tué par l'OS puis relancé,
  l'id est restauré et l'album est rechargé depuis Room.

### 4.5 Performance

- **Build release R8** : `isMinifyEnabled` + `isShrinkResources = true` ;
  APK 19 Mo (debug) → **2,9 Mo** (release) après minification, sans règle
  `keep` custom (les libs fournissent leurs règles consumer). Un `assembleRelease`
  fait partie des checks du livrable.
- **Listes** : `LazyColumn` avec `key` *et* `contentType` sur les items —
  l'identification et la réutilisation des composables sont corrects ; la
  requête d'item image est mémoïsée (`ImageRequest` via `remember(album)`)
  pour ne pas relancer le pipeline Coil à chaque recomposition (toggle
  favori, scroll).
- **Images** : Coil 3 gère mémoire + disque ; requêtes dimensionnées par
  les contraintes `aspectRatio`/`heightIn` (pas de décodage plus grand que
  l'affichage), UA header commun à toutes les requêtes.
- **Flows** : `SharingStarted.WhileSubscribed(5_000)` — l'observation Room
  est stoppée en arrière-plan (aucune re-query UI invisible), chaque
  émission se limite aux diff Room.

---

## 5. Réseau : Retrofit + kotlinx-serialization

Le graphe réseau vit entièrement dans `DataModule` (`:data`), installé dans
le scope singleton Hilt :

- **Retrofit 3 + convertisseur kotlinx-serialization** (Jakewharton
  `retrofit2-kotlinx-serialization-converter`) : parsing sans réflexion,
  `Json { ignoreUnknownKeys = true; coerceInputValues = true }` pour tolérer
  l'évolution du payload sans crash (champ ajouté, supprimé, type null
  inattendu). La `BASE_URL` (fichier JSON statique de l'énoncé) vit dans
  l'interface `AlbumApiService`.
- **OkHttp** : `HttpLoggingInterceptor` niveau `BODY` — activé uniquement en
  debug (`BuildConfig.DEBUG`), aucun log de payload en release.
- **DTO = seule représentation réseau** : `AlbumDto` vit dans `:data` et
  n'est jamais exposé à l'UI (§2.2). Le mapping DTO → Entity → domaine est
  trivialement testable (§7).
- **Stratégie d'erreur** : aucune transformation silencieuse côté data —
  `refresh()` laisse l'exception remonter et c'est au ViewModel de décider
  (état dérivé, cf. §7.1). Timeouts et reconnect restent les défauts OkHttp,
  suffisants pour ce périmètre ; une évolution possible est au §9.
- **Images (Coil 3)** : la même base réseau sert les images via Coil avec
  un `User-Agent` d'application (`LeboncoinApp/1.0`) posé sur chaque
  `ImageRequest`, plutôt que la valeur par défaut du loader — cf. §6, et
  l'entrée 6 du catalogue §8.

## 6. UI : Compose + Spark design system

- **Spark 4.3.1 (design system Adevinta)** : composants (cards, chips,
  top bar, navigation bar), typographie et tokens de couleur. Points
  d'attention écosystème documentés au §8 : icônes renommées entre 1.x et
  4.x (`SparkIcons` → `LeboncoinIcons`), et dark mode non automatique.
- **`AppTheme`** : wrapper du thème Spark qui synchronise le jeu de couleurs
  avec `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` —
  sinon les icônes système restent blanches sur fond clair en mode nuit
  (entrée 4 du §8).
- **Un seul shell** : un unique `Scaffold` dans `MainActivity` porte la top
  bar (titre par destination, flèche retour + favori sur le détail) et la
  bottom bar (2 onglets). Il fournit le `PaddingValues` consommé par chaque
  écran : les insets sont consommés une seule fois (entrée 9 du §8), aucun
  Scaffold imbriqué.
- **Écrans "content-only"** : `AlbumsScreen`, `FavoritesScreen`,
  `DetailsScreen` ne sont que du contenu à état (`AlbumsUiState` etc.) —
  sans barre propre, ils sont réutilisables en preview et testables tels
  quels.
- **Transitions** : cross-fade entre onglets (`tween(220)`), slide pour
  l'entrée/sortie du détail (`tween(300)`), et **shared element** de la
  vignette vers l'image du détail via un `SharedTransitionLayout` + deux
  `CompositionLocal`s (helper `sharedImageElement`, cf. §4) : une transition
  visuelle continue sans coupler les écrans à la navigation.
- **Coil 3** : chargement mémoire + disque, requêtes construites une fois
  par item (`remember(album)`) — une recomposition déclenchée par un toggle
  favori ne relance pas le téléchargement d'image — et `contentType`
  positionné sur les items des deux listes pour recycler efficacement.
- **Animations de liste** : les items des deux listes portent
  `Modifier.animateItem()` — apparition et disparition en fondu,
  repositionnement glissé : retirer un favori fait fondre la carte pendant
  que ses voisins se resserrent, plutôt qu'une disparition brutale.
- **Accessibilité / textes** : `contentDescription` basés sur les ressources
  `strings.xml`, pas de chaîne en dur ; états vides et d'erreur affichés
  (favoris vide, erreur réseau avec bouton Retry).
- **Localisation (EN par défaut + FR)** : toute chaîne UI vit dans
  `strings.xml` — valeurs par défaut EN (`values/`), traduction FR
  complète (`values-fr/`), format strings pour les chips d'id
  (« Album n° %1$d », « Photo n° %1$d ») et pluriels `quantityString`
  pour le compteur de favoris (« 1 favori » / « X favoris »). Côté
  packaging : `android:localeConfig` (Android 13+ : l'utilisateur choisit
  la langue de l'app indépendamment du système) et `localeFilters =
  [en, fr]` (ressources épurées du reste).

---

## 7. Tests

### 7.1 Ce qui est testé (18 tests, tous JVM, aucune dépendance mock externe)

| Suite | Fichier | Ce qu'elle couvre |
|---|---|---|
| `AlbumsViewModelTest` (6) | app | Loading→Success, erreur sans cache (jamais de crash), **cache servit même réseau coupé** (inverse : offline-first), retry **avec fenêtre Loading observée** + garde anti double-clic, aller-retour favori |
| `DetailsViewModelTest` (3) | app | lecture par id, **id inconnu → null** (fallback, pas de crash), toggle favori |
| `FavoritesViewModelTest` (3) | app | onglet vide initial (état valide), propagation des toggles, un-favori retiré |
| `AlbumRepositoryTest` (6) | data | refresh → write-through cache, **échec réseau propagé sans toucher le cache**, mapping entité+favori → modèle domaine, lecture par id (connu/inconnu), contrat d'onglet favoris, **échec de refresh qui ne touche jamais les favoris** |

### 7.2 Stratégie

- **Fakes in-memory, pas de mocking framework** : `FakeAlbumApi`
  (Result<>, succès/échec par test) et `FakeAlbumDao`/`InMemoryAlbumDao`
  (deux collections, sémantique REPLACE) — on teste le **comportement
  observable**, pas l'implémentation.
- **TestDispatcher** (`Dispatchers.setMain`) + `advanceUntilIdle` : les
  ViewModels tournent sur `viewModelScope` (Main.immediate) — chaque état
  intermédiaire (Loading notamment) est observable explicitement.
- **Turbine** pour observer les StateFlow/Flow : première émission = valeur
  initiale, et piège documenté — un StateFlow partagé **déduplique** les
  valeurs identiques (voir le test « id inconnu reste null »).
- Les fakes atterrissent là où vit le code testé : les ViewModels (module
  app) partagent `Fakes.kt`, la repository (module data) embarque ses
  propres petites fakes in-memory — chaque module JVM-runnable sans émulateur.

### 7.3 Lancer les tests

```bash
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest   # 18 tests JVM
./gradlew :app:assembleDebug :app:assembleRelease          # builds + R8
```

La couverture ViewModel seule couvre aussi, par construction, la restauration
de la configuration/process death (SavedStateHandle reconstruit avec les
extras d'une Activity restaurée — cf. §4.4).

---

## 8. Bugs trouvés & corrigés

Catalogue des pièges débusqués dans le skeleton et au fil du développement.
Chaque entrée correspond à un vrai défaut observé, pas une hypothèse :

| # | Bug / piège | Cause racine | Correctif |
|---|---|---|---|
| **Crash au 1er lancement sans réseau** (rejetatoire) | fetch bloquant au démarrage, exception non gérée | Offline-first : Room d'abord, UI à 3 états (§3, §7.1) |
| **Favoris réinitialisés** à chaque refresh | `insertAll(REPLACE)` écrasait la table qui portait aussi le favori | Table `favorites` séparée, REPLACE ne touche que les albums (§3) |
| **Album passé en extra de navigation** | modèle domaine couplé à Android (`Parcelable`) | Navigation par **id** + ré-observation Room via `SavedStateHandle` (§4) |
| Icônes système blanches sur fond blanc en dark mode | Spark ne bascule pas seul les system bars | `AppTheme` synchronise couleurs + `isAppearanceLight*` (§6) |
| **Fuite de Context** dans l'analytics | helper réassignait une Activity | Context applicatif injecté une fois (singleton) |
| Images refusées par le CDN | `User-Agent` par défaut de Coil | Header `LeboncoinApp/1.0` sur chaque `ImageRequest` (§5) |
| Top bar cachée/repliée à l'écran détail | l'écran pilotait sa propre barre | top bar permanente du shell, titre/actions par route (§4) |
| Double marge de barre de statut | Scaffolds imbriqués consommant l'inset 2 fois | shell unique détenteur des insets (§6) |
| Icône favori introuvable après montée de version Spark | renommage 1.x→4.x `SparkIcons`→`LeboncoinIcons` | migration vers la nouvelle API (§6) |
| `hiltViewModel` déprécié | namespace déplacé (`androidx.hilt.lifecycle…`, 1.4.0) | import du nouveau namespace |
| **Retry sans feedback** (bouton figé sur Error) | aucun état "requête en cours" | flag `refreshing` dans le reduce dérivé (§7.1) |
| **Double-clic Retry → 2 requêtes** | pas de garde en entrée de `loadAlbums()` | retour anticipé si un load est en vol + test `callCount` |
| `ImageRequest` reconstruit à chaque recomposition | builder inline dans le composable | `remember(album)` + `contentType` (§6) |
| Conflits d'import material3 / spark après merge | mêmes noms de composables dans les deux | imports explicites reconciliés (`Card.Flat` Spark) |
| Test "fantôme" sur StateFlow | un StateFlow partagé déduplique les valeurs égales | convention de test documentée (§7.2) |
| Dépendances mortes du scaffold | `androidTest` inutilisé, `ExampleUnitTest`/`ExampleInstrumentedTest` | supprimés, classpath de test allégé |
| Chips d'id « Album #x » / « Track #x » en dur | littéraux `String` dans les composables | format strings + traduction FR complète (`values-fr`), cf. §6 |

---

## 9. Planification des évolutions

Feuille de route par priorité d'impact, critères de déclenchement inclus
(chaque item démarre quand le besoin devient réel, pas avant) :

**P0 — avant une mise en production**

1. **Toggle favori atomique côté DAO** : le double-clic est couvert côté
   refresh, mais deux toggles quasi simultanés (liste + détail) lisent puis
   écrivent — un `UPDATE` SQL atomique (ou une requête toggle) supprimerait
   la fenêtre de course.
2. ~~**Localisation FR**~~ — **livré** : `values-fr` complète, format
   strings pour les chips d'id, pluriels `quantityString` (compteur de
   favoris), `localeConfig` + `localeFilters` (cf. §6). Reste éventuellement
   un audit complet des pluriels/tailles de typographie sur d'autres locales
   si l'app s'internationalise.
3. **CI** (lint + tests JVM + assembleDebug/Release à chaque PR, cache
   Gradle) : la base est déjà prête — builds verts, 18 tests 100 % JVM sans
   émulateur.

**P1 — court terme**

4. **Tablet two-pane** : liste + détail côte à côte via `WindowSizeClass`,
   le `NavHost` actuel sert de base (les écrans content-only s'y prêtent).
5. **Tests instrumentés** ciblés : rotation et process death avec
   `ActivityScenario.recreate()`, screenshots des 3 états (Loading/Error/
   Success).
6. **Pagination + recherche** : Room `PagingSource` si le catalogue grandit,
   filtre par titre côté requête SQL (jamais en mémoire côté UI).

**P2 — si l'app grossit**

7. **Modularisation features-first** (cf. §2.3) : les packages
   `feature/*` (déjà en place) deviennent des modules Gradle quand une
   frontière d'équipe ou un temps de build le justifie.
8. **Baseline Profile** + Macrobenchmark : démarrage et first-frame du
   scroll (la liste image-heavy est le candidat naturel).
9. **Refresh réglé** : `NetworkBoundResource` (stale-while-revalidate)
   plutôt que le refresh unique, si l'API évolue en vraie ressource.
