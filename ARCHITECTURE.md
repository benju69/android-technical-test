# Architecture & choix techniques

> Document de justification des choix d'architecture, des patterns et des librairies
> appliqués (exigence de l'énoncé)

Application : **AndroidRecruitmentTestApp** : liste d'albums (photos), favoris, écran de détail, persistance offline.

Stack : Kotlin 2.4, Jetpack Compose, AGP 9.5 / Gradle 9.8, module `:app` +
module `:data`.

---

## Sommaire

1. [Injection de dépendances : Hilt](#1-injection-de-dépendances--hilt)
2. [Architecture par couches (MVVM + Repository)](#2-architecture-par-couches) — *à venir*
3. [Persistance offline : Room](#3-persistance-offline--room)
4. [Navigation](#4-navigation) — *à venir*
5. [Réseau : Retrofit + kotlinx-serialization](#5-réseau) — *à venir*
6. [UI : Compose + Spark design system](#6-ui) — *à venir*
7. [Tests](#7-tests) — *à venir*
8. [Bugs trouvés & corrigés](#8-bugs-trouvés--corrigés) — *à venir*
9. [Planification des évolutions](#9-planification-des-évolutions) — *à venir*

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
La description détaillée du module `:data` viendra avec sa refonte
(§2 Architecture par couches).

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
| Extension Navigation Compose | Il faudrait re-sérialiser l'objet en argument de route | Le pattern est déjà correct : l'argument de route EST un id (cf. §9) |

En pratique : `DetailsViewModel` lit l'id dans
`SavedStateHandle[ALBUM_ID_KEY]` (injecté automatiquement par Hilt dans le
constructeur du `@HiltViewModel`), expose
`album: StateFlow<Album?>` (null = inconnu → fallback), et `toggleFavorite()`
écrit sur la table favorites comme pour la liste. Un album n'existant pas
(ou cache vide avec réseau coupé) affiche la figure "work in progress"
plutôt qu'un crash — cohérent avec le critère « l'application ne doit pas
crasher systématiquement ».

### 4.2 Conception d'écran

`DetailsActivity` reste un composant minuscule : field injection analytics +
`viewModels()`, collection `collectAsStateWithLifecycle`, et délégation
totale à `DetailsScreen` (stateless, préviewable, testable sans activity). Le
bouton cœur (`FavoriteButton`) est **partagé** entre la liste et le détail
(`ui/FavoriteButton.kt`) : même interaction, mêmes strings
d'accessibilité, un seul endroit à faire évoluer.

### 4.3 Bout du chemin déjà documenté

Le passage à **Navigation Compose (single-activity)** est prévu au §9 : il
consistera essentiellement à remplacer la paire d'Activities par un NavHost
dont l'argument de route est… ce même id. Les ViewModels
(`@HiltViewModel` avec `SavedStateHandle` déjà branché) ne changent pas :
c'est la preuve que les couches de routage et d'état sont découplées.
