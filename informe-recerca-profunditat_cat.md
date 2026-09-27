# Resum Executiu

Aquest informe tècnic analitza les fonts de dades oficials per Catalunya (especialment Girona i Figueres) orientades a la fase 1 del projecte Fair Factory (API, feeds, scraping). Hem prioritzat els portals institucionals: l’**Ajuntament de Figueres** (transparència i contractació), la **Generalitat de Catalunya** (dades obertes regionals) i la **Diputació de Girona** (pressupost i informació econòmica). Es descriuen en detall les APIs, conjunts oberts i punts finals disponibles (URL base, formats JSON/CSV/XML, autenticació, paginació, límits, freqüència d’actualització, exemples de consulta i respostes). També s’identifiquen pàgines web candidats per *web scraping* (taulers de transparència, contractes, pressupostos, subvencions, padrons fiscals) amb selectores CSS/XPath per extreure-hi dades clau, patrons de paginació, mesures anti-bot i bones pràctiques de robustesa. S’inclouen suggeriments per mapar les dades obtingudes a l’esquema del projecte (per exemple, camp “objecte del contracte” o “import adjudicat” per a fitxers de contractació). Finalment oferim una llista de tasques prioritzades amb temps estimat per connector, exemples de codi (curl per a les API, plantilles Python `requests`/BeautifulSoup per scraping), i diagrames Mermaid de l’arquitectura de connectors. Aquest document és analític i complet, i inclou taules comparatives de fonts i diagrames esquemàtics.  

## Fonts i APIs prioritzades

**Ajuntament de Figueres (Dades Obertes)**: L’Ajuntament de Figueres disposa d’un portal de dades obertes basat en CKAN gestionat pel Consorci AOC (Seu Electrònica). La plataforma publica múltiples conjunts de dades (per exemple, *“Estatuts”*, *“Ordenances fiscals”*, *“Actes de Ple”*, *“Convocatòries de subvencions”*, *“Calendaris i padrons fiscals”*, etc.) amb cobertura de tots els ens locals. Les dades són accesibles via l’API CKAN de la Seu Electrònica. La URL base és `https://dadesobertes.seu-e.cat/api/`. Per exemple, per al dataset “Ordenances fiscals” s’usa:

```
GET https://dadesobertes.seu-e.cat/api/action/datastore_search?resource_id=dbfe266c-da98-4f4c-9296-b16f40e6b23f&filters={"CODI_ENS":"1706690004"}
```

Aquest servei (format JSON) retorna les files amb filtre pel codi de municipi (“CODI_ENS” de Figueres). També s’ofereix una consulta OData (JSON) equivalent:

```
GET https://dadesobertes.seu-e.cat/api/aoc/action/odata/dbfe266c-da98-4f4c-9296-b16f40e6b23f?$format=json&$q={"CODI_ENS":"1706690004"}
```

La resposta és JSON amb camps corresponents. La plataforma permet descarregar CSV dels conjunts (i també té `package_show` i `package_search` per meta-dades). No cal autenticació per a l’accés de lectura. No es documenten límits explícits, però es recomana poliment respectar el volum de peticions (p. ex. paginació amb paràmetres `limit` i `offset`). Cada dataset indica data d’actualització (p. ex. *Última actualització: 26/09/2026*) i, en general, es pot suposar actualització periòdica (anual o segons normativa).  
Els conjunts de dades específics (amb els corresponents resource_id, totes multi-ens) inclouen:
- **Estatuts** – ID `fc660dac-21cc-444c-8bef-00e13eb01520`, descriptiu de l’organització interna. Exemple d’API.
- **Plecs de clàusules generals** – ID `ed9ac827-1734-412f-a33a-4c85fa850d7e`. 
- **Càrrecs electes** – ID `eb131bb1-f521-4aeb-9004-2fea1f372e89` (polítics elegits).
- **Ordenances fiscals** – ID `dbfe266c-da98-4f4c-9296-b16f40e6b23f` (normativa d’impostos locals).
- **Convocatòries de personal (resultats)** – ID `d9f131a1-5489-4cd7-a8ab-902b99df7968`.
- **Registre funcionaris habilitats** – ID `c633d5e6-b3b4-4ca1-8fc6-9e7c049c1e29`.
- **Actes de Ple** – ID `b5d370d0-7916-48b6-8a69-3c7fa62a1467`.
- **Convocatòries de subvencions i ajuts** – ID `b39a563c-3b96-4e64-a856-e313458f3dad` (vegeu font CIDO de Diputació de Barcelona en la descripció).
- **Dades generals de l’ens** – ID `ab53cbf3-a439-4f59-a2f5-658bee1994e5`, carrega informació des de l’INE i Generalitat (font MUNICAT).
- **Calendaris i padrons fiscals** – ID `59218973-d6a6-4cc1-bd3f-c9b9b851cb53`.
  
Tots aquests conjunts es poden consultar per Ajuntament. Els *resource_id* i l’API es poden obtenir del CKAN (`package_search` o `package_show`, cf.). Per exemple, la crida `package_show?id=agn-n-estatuts` retorna metadades JSON del dataset d’estatuts.  

**Ajuntament de Figueres (Perfil de contractant)**: La seu electrònica inclou pàgines de transparència relacionades amb contractació. En particular, el llistat de *“Licitacions en tràmit (perfil de contractant)”* (vegeu [Licitacions en tràmit (perfil de contractant)](https://seu-e.cat/ca/web/figueres/govern-obert-i-transparencia/contractes-convenis-i-subvencions/relacio-de-contractes/licitacions-en-tramit-perfil-de-contractant)). Aquesta pàgina web mostra en forma tabular les licitacions i adjudicacions (títol, import, procediment, dates, enllaços a perfils oficials). **No hi ha API pública documentada per a això**, cal fer scraping del HTML. El tauler té estructures tipus `<table>` amb files `<tr>` i columnes `<td>`. Per exemple, un fragment de la taula: 

```
Títol │ Òrgan de contractació │ Codi exp. │ ... │ Pressupost bàsic │ ... │ Enllaç Documents
Contracte del servei de muntatge... │ Ajuntament de Figueres │ 21/2018 │ ... │ 63 428.0 │ ... │ https://contractaciopublica.gencat.cat/… 
```

Cada fila es pot identificar mitjançant selectores CSS (p.e. `table tr`) o XPath (p.e. `//table//tr`). La col·laboració de Contractació Oberta indica que la font original és `contractaciopublica.cat`. Recomanem limitar la freqüència de peticions (p.e. un cop per segon) per no sobrecarregar el portal, i respectar el robot.txt si aplica (a *seu-e.cat* no hi ha restriccions explícites de simples rastrejadors, però cal tenir cura amb les peticions massives).  

**Generalitat de Catalunya (Dades Obertes Socrata)**: El Govern de la Generalitat disposa d’una plataforma de dades obertes basada en Socrata (domini `analisi.transparenciacatalunya.cat`, vinculat al portal *Dades Obertes de Catalunya*). Per exemple, el dataset *“Execució mensual del pressupost de la Generalitat de Catalunya (Despeses)”* es pot obtenir directament en formats CSV/JSON/XML. Les adreces són, per la versió pública:

- CSV: `https://analisi.transparenciacatalunya.cat/api/v3/views/ajns-4mi7/export.csv?accessType=DOWNLOAD`  
- JSON: `https://analisi.transparenciacatalunya.cat/api/v3/views/ajns-4mi7/query.json?accessType=DOWNLOAD`  
- (XML anàleg, amb `export.xml`).
  
Aquest endpoint proporciona les dades en format tabular (presumiblement, informació acumulada de despesa per mes). Cal atenció que Socrata limita via quotas (sense `App-Token` poden aparèixer límits d’ús); es pot configurar un *X-App-Token* per crides massives. La resposta JSON pot requerir paginació (`$limit`, `$offset`) si són moltes files (límit per defecte és 1000). La plataforma també disposa d’una interfície web de consulta/filtres. S’hi poden trobar altres conjunts rellevants (p.e. personal de sanitat, informacions de subvencions, etc.). No hem detectat un portal API català únic més enllà de Socrata; els departaments poden publicar també conjunts a `dadesobertes.gencat.cat` (portal general de govern obert).  

**Diputació de Girona (Pressupost)**: La Diputació publica anualment els seus pressupostos i documents relacionats en format PDF a la seu electrònica de la Diputació. No ofereix API estructurada, però la pàgina web per a **Pressupost** (ex. [Pressupost general de 2026][73]) conté enllaços descarregables. Per exemple, s’hi mostren taules amb enllaços a PDFs: el *Pressupost 2026* té enllaç `https://seu.ddgi.cat/pressupost/2026.pdf` (i al·legacions, comptes…). Per extreure dades, caldria escrapejar la pàgina HTML o directament baixar i processar els PDFs amb OCR/lectors de PDF. No hi ha formats JSON/CSV, només PDF oficial.  
Altres fonts de la Diputació (contractació, subvencions) es gestionen per separat: existeix un perfil de contractant integrat al portal de contractació de la Generalitat (enllaç des de [73†L174-L182] “Perfil del contractant”), i el tauler d’anuncis (`tauler.seu.cat`) és genèric per a tots els ens AOC i cal un identificador (la nostra consulta va redirigir a `tauler.seu-e.cat?idEns=1706690004` per Figueres i a `tauler.seu.cat` per Diputació). Aquest tauler també es podria scrapear amb identificador d’ens.  

## Scraping de pàgines web

- **Licitacions i adjudicacions (Figueres)**: La pàgina de *Licitacions en tràmit (perfil de contractant)* de Figueres (https://seu-e.cat/.../licitacions-en-tramit-perfil-de-contractant) conté la informació de contractes en format de taula HTML. Es pot fer scraping amb BeautifulSoup: buscar `table` i recórrer cada `tr`. Per cada fila, obtenir les columnes (p. ex. `row.find_all('td')`). Exemple:

  ```python
  import requests
  from bs4 import BeautifulSoup

  url = "https://seu-e.cat/ca/web/figueres/govern-obert-i-transparencia/contractes-convenis-i-subvencions/relacio-de-contractes/licitacions-en-tramit-perfil-de-contractant"
  res = requests.get(url, headers={"User-Agent": "Mozilla/5.0"})
  soup = BeautifulSoup(res.text, "html.parser")
  table = soup.find("table")
  for tr in table.find_all("tr")[1:]:  # saltar capçalera
      cols = [td.get_text(strip=True) for td in tr.find_all("td")]
      print(cols)
  ```
  Aquí cada `cols` conté llista de valors de les columnes (títol, òrgan, import, etc). Cal considerar que alguns camps contenen enllaços (p.ex. *Documents*, *Més info*) que es poden extreure de les etiquetes `<a>` en els `td`. Caldrà capturar l’URL o text corresponent. No sembla haver-hi paginació: tot el llistat és a una sola pàgina web. Si apareixen advertiments contra el *scraping*, es recomana identificar-se amb header i respectar els intervals de sol·licitud (p.ex. un retard d’1-2 segons entre peticions).

- **Tauler d’anuncis de Figueres**: S’ofereix accés via un enllaç extern [`tauler.seu.cat`](https://tauler.seu.cat/inici.do?idens=1706690004) (redirecció a `tauler.seu-e.cat`). Aquest és un servei genèric AOC per a tots els ens. Es pot carregar amb requests, però la sessió pot requerir operacions CAS. Una possible estratègia és fer HTTP GET a l’URL amb el paràmetre `idens=1706690004` per Figueres i parsejar l’HTML resultant. Caldrà tractar amb JavaScript mínim (pot mostrar formularis). En tot cas, el tauler permet obtenir edictes i anuncis actius. Si cal, es pot extracció la informació del DOM (típicament taules de publicacions i dates).

- **Pressupostos i comptes**: Al web de transparència es mostren botons i fitxes amb referència a PDFs (Pressupost, Liquidació, Compte General). Tanmateix, les pàgines de l’Ajuntament carreguen el contingut mitjançant AJAX i no exposen directament llistats en HTML estàtic (vegeu [57] i [56]). Per això, per a Figueres és molt més fiable utilitzar el CKAN (ja conté dades pressupostàries en els conjunts “Despeses per programa”, etc. encara que aquests es carreguen dinàmicament; en canvi, a CKAN no n’hem trobat directament). Suggereixo per als pressupostos municipals usar el portal CKAN (si existís) o sol·licitud manual a l’Ajuntament. Per la Diputació, els PDFs s’accedeixen des de [73], i es podria fer un *download* directe (codi de connector: wget/requests sobre `https://seu.ddgi.cat/...pdf`).  

- **Padrons i tributs**: L’AOC publica el dataset “Calendaris i padrons fiscals” amb ID `59218973-d6a6-4cc1-bd3f-c9b9b851cb53`. La info bruta de padró (amb càrrecs per tributs) no està accessible per web pública sinó via aquest CKAN. Si es volia scrapear, caldria trobar l’aplicació tributària local (ex. gespaud, padró web), però normalment és interna o per a ciutadans autenticats. Així que s’aconsella l’enfocament CKAN/APIs obertes.

## Formats, paginació i límits

- **Formats**: Les APIs de CKAN retornen JSON per defecte (`datastore_search` retorna JSON). Els endpoints OData (`api/aoc/action/odata/…?$format=json`) també donen JSON. El portal AOC permet descarregar CSV directament (amb `export.csv`). El Socrata (`analisi.transparenciacatalunya.cat/api/v3`) admet CSV, JSON i XML com mostren les referències. Els elements de dades ofereixen camps estàndard (text, numèrics, dates).  

- **Paginació**:  
  - CKAN `datastore_search` retorna fins a 100 records per defecte; es pot ajustar `limit=<n>` i `offset=<m>` per paginar. Ex. `...?limit=1000&offset=0`.  
  - CKAN `package_search` i `package_show` són per metadades (no dades).  
  - Socrata utilitza paràmetres `$limit` i `$offset` (ex. `.../query.json?$limit=5000&$offset=0`). Sense paràmetre, retorna els primers 1000 registres.  
  - Les consultes per la pàgina web de l’Ajuntament (scraping) no paginen automàticament, tot pot ser en una sola pàgina; si n’hi hagués llistats extensos normalment es repeteix l’estructura en diverses pàgines HTML o hi ha controls “segmentats”. S’haurà de detectar en cas que aparegui més enllà de les 20-30 entrades.  

- **Autenticació**: Les APIs són obertes (no calen claus) quan es tracta de dades públiques. Cal comprovar si el portal de contractació té restriccions: *contractaciopublica.cat* sol ser públic i accessible mitjançant enllaços, però ofereix una API (SICEP) només per als seus usuaris registrats. Aquí només extraiem via scraping.  

- **Rate Limits**: No hem trobat límits oficials divulgats per l’AOC/CKAN. En general s’assumeix ús moderat (alguns centenars de consultes al dia) sense problemes. Socrata té quotes (amb `X-App-Token` es poden executar més consultes per minut/hora). Recomanem usar sintaxi de paginació i considerar pauses breus.  

- **Freqüència d’actualització**: Cada conjunt de dades indica la darrera actualització (p. ex. *26/09/2026* a molts conjunts de Figueres). Molts són actualitzats anualment (pressupost, estadístiques). Les dades de contractació s’actualitzen sovint (cada cop que es publiquen llistats oficials). Per la Generalitat, el pressupost s’actualitza mensualment (executió). Cal preveure recarregar segons periodicitat (potser programar descàrregues mensuals per Socrata).  

## Suggeriments de mapeig de dades

Tot i no disposar de l’esquema intern, podem suggerir com emmagatzemar camps rellevants:
- **Contractació pública**: camp “títol” (descripció), “òrgan contractant”, “import convocat”, “importe adjudicat”, “data licitació”, “data adjudicació”, “procediment”, “adjudicatari”. Aquestes apareixen als datasets o taulers. Exemple: en la taula de licitacions trobem títol (primer camp), pressupost bàsic (camp “Pressupost bàsic de licitació”), import estimat, l’adjudicació provisional o definitiva (enllaços als avisos PDF).  
- **Pressupostos**: camps “any”, “capítol d’ingrés/despesa”, “import”. El dataset “Execució pressupost” té desglossament per àmbits.  
- **Subvencions/ajuts**: nom de subvenció, objecte, import, exercici, requisits. El dataset de convocatòries ho inclou en text.  
- **Pressupost Ajuntament**: si es troba, guardar “per programa” i “per naturalesa” separats, amb exercici.  
- **Padrons fiscals**: per a cada impost i període, impost, percentatge, període.  
- **Dades bàsiques ens locals**: el conjunt “Dades generals” té codi INE, nom, tipus d’ens, població. Es pot mapar a una taula d’entitats amb codi/nom/adreça.  

Els connectors (veure diagrama) emmagatzemaran registres homogenitzats; p.e. tots els contractes podrien anar a una col·lecció “Contractes” amb etiqueta localitat=Figueres (identificador 1706690004).  

## Comparativa de fonts

| Font / Portal                                            | Tipus de dades                                    | Accés & formats                    | Autenticació | Mostra de dades                                                                                            |
|----------------------------------------------------------|---------------------------------------------------|------------------------------------|-------------|------------------------------------------------------------------------------------------------------------|
| **Figueres – Dades Obertes (CKAN)**         | Ordenances, estatuts, acta de ple, etc.            | API CKAN: JSON; OData: JSON; CSV    | No          | Conjunts tabulars generalitzats. Exemple: `datastore_search?resource_id=...&filters={"CODI_ENS":"1706690004"}`. |
| **Figueres – Perfil de contractant (seu-e)**| Licitacions i adjudicacions de contractes públics. | HTML (taules enllaçades); CSV no   | No          | Taula HTML amb columnes com Títol, Expedient, Pressupost, Adjudicatari, enllaços a contractació oficial.                    |
| **Generalitat – Socrata “Analisi Transparència”** | Pressupost general, execució, personal, etc.  | API Socrata: JSON/CSV/XML           | Pot requerir App-Token en sol·licituds massives | CSV/JSON amb dades financeres oficials. Ex. Execució pressupost: `views/ajns-4mi7/export.csv`. |
| **Dip. Girona – Seu Electrònica (Pressupost)**      | Pressupost, pressupost d’organismes, al·legacions    | PDF (al web seu.ddgi.cat)           | No          | Documents PDF per any (Pressupost general, al·legacions) llistats en taula web.                                                             |
| **Contractaciopublica.cat (CAT)**                        | Perfils de contractant de totes admin. catalanes.   | Web/API (restringida)              | Públic      | Està present a Contratació Oberta; però millor via scraping o CKAN local.                                                   |
| **Tauler AOC (tauler.seu.cat)**                           | Edicts i anuncis de transparència.                 | HTML (formulari per idEns)         | No          | Informació de convocatòries i edictes, cal usar paràmetre `idens`.                                                                                       |

## Arquitectura de connectors (Mermaid)

```mermaid
flowchart TD
    subgraph "Administracions"
      AFigueres[Ajuntament de Figueres]
      AGov[Generalitat de Catalunya]
      ADiputacio[Diputació de Girona]
    end
    subgraph "Connectors Fair Factory"
      C_FigOpen[Figueres - Dades Obertes API]
      C_FigTransp[Figueres - Transparència (Scraping)]
      C_GenSocrata[Generalitat - Socrata API]
      C_DipPress[Diputació - Pressupost PDFs]
    end
    subgraph "Base de Dades Fair Factory"
      DB[(Fitxers / BD de l'aplicació)]
    end
    AFigueres --> C_FigOpen --> DB
    AFigueres --> C_FigTransp --> DB
    AGov --> C_GenSocrata --> DB
    ADiputacio --> C_DipPress --> DB
```

Aquest diagrama indica els connectors previstos per accedir a cada font i emmagatzemar les dades al repositori del projecte. Per exemple, **C_FigOpen** obté dades del portal CKAN de Figueres, mentre que **C_FigTransp** fa scraping de pàgines web de transparència. 

## Implementació i esforç estimat

A continuació oferim un checklist prioritzat i una estimació d’esforç per connector:

1. **Connector CKAN Ajuntament de Figueres** – *Esforç: 6h*.  
   - Desenvolupar codi per cridar l’API CKAN (`requests.get` o `curl`) per cada resource_id rellevant (veure llistat més amunt).  
   - Processar JSON i mapar camps (aplicar filters per CODI_ENS).  
   - Exemples de codi:  
     ```bash
     curl -G "https://dadesobertes.seu-e.cat/api/action/datastore_search" \
       --data-urlencode "resource_id=dbfe266c-da98-4f4c-9296-b16f40e6b23f" \
       --data-urlencode 'filters={"CODI_ENS":"1706690004"}'
     ```  
     ```python
     import requests

     res = requests.get(
         "https://dadesobertes.seu-e.cat/api/action/datastore_search",
         params={"resource_id": "...", "filters": '{"CODI_ENS":"1706690004"}'},
     )
     data = res.json()["result"]["records"]
     ```  
   - Gestionar paginació (propagar `limit`/`offset`) si hi ha molts registres.  
   - Emmagatzemar dades en l’esquema intern. 

2. **Connector scraping contractació Figueres** – *Esforç: 4h*.  
   - Fer `requests.get` de la URL de perfil de contractant i parsejar amb BeautifulSoup.  
   - Extreure taula de licitacions/adjudicacions (títol, codi, import, dates). Exemple de plantilla Python provista més amunt.  
   - Detectar si hi ha paginació (no sembla en un sol cop).  
   - Mapejar camps a l’esquema (per ex. assignar camp `title`, `expedient`, `import`, `contractant`).  

3. **Connector Socrata Generalitat** – *Esforç: 4h*.  
   - Cridar l’API Socrata (CSV/JSON) per als conjunts d’interès. Ex.: pressupost.  
   - Gestionar autòretry amb `X-App-Token` si cal.  
   - Codi snippet:  
     ```bash
     curl -H "X-App-Token: VOSTRE_TOKEN" \
       'https://analisi.transparenciacatalunya.cat/api/v3/views/ajns-4mi7/query.json?accessType=DOWNLOAD'
     ```  
     ```python
     import requests

     url = "https://analisi.transparenciacatalunya.cat/api/v3/views/ajns-4mi7/query.json"
     res = requests.get(url, params={"accessType": "DOWNLOAD"}, headers={"X-App-Token": "XXX"})
     rows = res.json()["data"]
     ```  
   - Desar JSON/CSV al sistema.

4. **Connector Dip. Girona Pressupost (descàrrega PDF)** – *Esforç: 3h*.  
   - Analitzar HTML del pressupost per recollir enllaços a PDFs (identificats per exercici).  
   - Exemple de scraping amb `requests/BeautifulSoup` per llistat de PDFs i després `requests.get` per descarregar-los.  
   - Emmagatzemar o processar segons sigui necessari (OCR text opcional).  
   - Exemple de codi:  
     ```python
     res = requests.get("https://www.ddgi.cat/web/nivell/530/s-0/pressupost")
     soup = BeautifulSoup(res.text, "html.parser")
     for a in soup.select("a[href$='.pdf']"):
         url_pdf = a["href"]
         resp = requests.get(url_pdf)
         open(a.text + ".pdf", "wb").write(resp.content)
     ```  
   - Enmagatzemar info en BD (sivella: fitxer binari o enllaç).

5. **Connector Tauler AOC** – *Esforç: 2h (opcional)*.  
   - Opcionalment, fer GET a `https://tauler.seu.cat/inici?idens=1706690004` i parsejar tauler de publicacions per a Figueres.  
   - Cal ajustar headers i cookies; pot requerir simular navegació CAS. Pot executar-se manualment o amb Selenium si cal.  

## Exemples de codi i consultes

- **Curl CKAN Figueres** (va obtenir retorn JSON):  
  ```
  curl -G 'https://dadesobertes.seu-e.cat/api/action/datastore_search' \
       --data-urlencode 'resource_id=dbfe266c-da98-4f4c-9296-b16f40e6b23f' \
       --data-urlencode 'filters={"CODI_ENS":"1706690004"}'
  ```

- **Python requests (Generalitat Socrata)**:  
  ```python
  import requests

  url = "https://analisi.transparenciacatalunya.cat/api/v3/views/ajns-4mi7/query.json"
  res = requests.get(url, params={"accessType": "DOWNLOAD"})
  data = res.json()["data"]
  ```
  (Afegir `headers={"X-App-Token":"XXX"}` si s’utilitza token per evitar límits).

- **BeautifulSoup (Taula de contractes Figueres)**:  
  ```python
  import requests
  from bs4 import BeautifulSoup

  res = requests.get(
      "https://seu-e.cat/ca/web/figueres/.../licitacions-en-tramit-perfil-de-contractant"
  )
  soup = BeautifulSoup(res.text, "html.parser")
  headers = [th.get_text() for th in soup.select("table th")]
  rows = []
  for tr in soup.select("table tr")[1:]:
      vals = [td.get_text(strip=True) for td in tr.find_all("td")]
      rows.append(dict(zip(headers, vals)))
  ```

- **Descàrrega PDFs (Dip. Girona)**:
  ```python
  res = requests.get("https://www.ddgi.cat/web/nivell/530/s-0/pressupost")
  soup = BeautifulSoup(res.text, "html.parser")
  links = [a["href"] for a in soup.select("a[href$='.pdf']")]
  for url in links:
      pdf = requests.get(url).content
      with open(url.split("/")[-1], "wb") as f:
          f.write(pdf)
  ```

## Taules comparatives

| **Conjunt de dades**           | **Endpoint/URL**                                                                         | **Format**  | **Filtres/paginació**              | **Auth.** | **Freqüència**      |
|-------------------------------|-----------------------------------------------------------------------------------------|-------------|------------------------------------|-----------|---------------------|
| Estatuts Figueres             | `GET /api/action/datastore_search?resource_id=fc660dac-21cc-444c-8bef-00e13eb01520&filters={"CODI_ENS":"1706690004"}` | JSON (CSV)  | `filters={"CODI_ENS":...}`, `limit/offset` | No        | Actualitzat 26/09/2026 |
| Ordenances fiscals Figueres   | `GET /api/action/datastore_search?resource_id=dbfe266c-da98-4f4c-9296-b16f40e6b23f&...` | JSON (CSV)  | Igual com l’anterior               | No        | Actualitzat 26/09/2026 |
| Dades generals ens Figueres   | `resource_id=ab53cbf3-a439-4f59-a2f5-658bee1994e5`                        | JSON (CSV)  | Igual                             | No        | 26/09/2026         |
| Etc. (CKAN Aj. Figueres)      | *Cadascun dels esmentats codi de rec.* (veure text)                                     | JSON, CSV   | Filtres CODI_ENS, limit/offset    | No        | Diversa (anual)    |
| Licitacions Aj. Figueres      | Web scraping: https://seu-e.cat/.../licitacions-en-tramit-perfil-de-contractant | HTML taula  | Cap (única pàgina)               | No        | Dinàmic (setmanal) |
| Tauler edictes Figueres       | Web scraping: https://tauler.seu.cat/inici.do?idens=1706690004            | HTML        | Paràmetre `idens`                | No        | Dinàmic (setmanal) |
| Pressupost Generalitat        | `GET /api/v3/views/ajns-4mi7/query.json?accessType=DOWNLOAD`             | JSON (CSV)  | `$limit/$offset`                 | X-App-Token (opc.) | Mensual (execució)  |
| Altres GDP/estadístiques Gov  | Similar Socrata (p.e. bases IRPF, ocupació) (consultar portal)                          | JSON/CSV    | Varis per dataset                | X-App-Token | Annual, trimestral  |
| Pressupost DDGI PDF           | Web: https://www.ddgi.cat/.../pressupost (vegeu [73])                                    | PDF (HTML)  | Pagina HTML genera taula          | No        | Anual (0nove.)     |

## Conclusió

Per a la fase 1 del projecte, es prioritzaran els connectors a fonts oficials catalanes. El primer pas és implementar els connectors d’API per a l’Ajuntament de Figueres (usant CKAN/AOC) i la Generalitat (Socrata). Simultàniament, es prepara el scraping de la pàgina de contractació de Figueres i, opcionalment, del tauler d’anuncis. La Diputació de Girona aporta principalment PDFs de pressupost; pot tractar-se com a fitxers de dades adjunts. Aquests connectors proveiran al repositori Fair Factory de dades estructurades, integrables en els models existents. 

**Fonts:** Hem obtingut informació dels portals oficials: el portal de dades obertes de Figueres, el catàleg de dades del Govern català, i la pàgina de transparència de la Diputació de Girona, entre altres. Les URLs i exemples concrets ja s’han enumerat en el text anterior.