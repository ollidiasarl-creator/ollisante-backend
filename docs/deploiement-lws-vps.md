# Déploiement sur le VPS LWS (api.ollisante.com)

Hypothèse : VPS sous Ubuntu 22.04/24.04 ou Debian 12, avec accès administrateur (root ou sudo).
Les commandes sont à exécuter par vous ou votre développeur : je n'ai aucun accès à votre serveur.

> ⚠️ **Santé et hébergement** : avec des patients en France, vérifiez auprès de LWS si l'offre est certifiée HDS
> (hébergeur de données de santé). Une offre VPS standard ne l'est généralement pas : à faire valider avant le lancement public.

## 0. Avant de commencer
- Dans la gestion DNS d'`ollisante.com`, créer un enregistrement **A** : `api` → adresse IP du VPS.
- Se connecter par clé SSH, jamais par mot de passe simple.

## 1. Sécuriser le serveur
```bash
sudo adduser deploy && sudo usermod -aG sudo deploy        # utilisateur d'administration (puis se reconnecter avec lui)
sudo apt update && sudo apt -y upgrade
sudo apt -y install ufw fail2ban
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
```
PostgreSQL ne doit **pas** être ouvert sur Internet (le port 5432 n'est pas autorisé ci-dessus : c'est voulu).

## 2. Installer les logiciels
```bash
sudo apt -y install openjdk-17-jre-headless postgresql nginx certbot python3-certbot-nginx
```

## 3. Base de données
```bash
openssl rand -base64 24            # génère un mot de passe : le noter dans votre gestionnaire de mots de passe
sudo -u postgres psql -c "CREATE USER ollisante WITH PASSWORD 'LE_MOT_DE_PASSE_GENERE';"
sudo -u postgres psql -c "CREATE DATABASE ollisante OWNER ollisante;"
```

## 4. Compte système et dossiers
```bash
sudo useradd --system --home /opt/ollisante --shell /usr/sbin/nologin ollisante
sudo install -d -o ollisante -g ollisante /opt/ollisante
sudo install -d -m 750 -o root -g ollisante /etc/ollisante
```

## 5. Mettre les clés (dont PawaPay test) sur le serveur
**Principe : les clés ne transitent jamais par un chat, un e-mail, un dépôt Git ou une capture d'écran.**
Elles vont dans un fichier lisible uniquement par le service.

1. Récupérer le **jeton API du bac à sable (sandbox)** dans votre tableau de bord pawaPay (section des jetons API
   de l'environnement *sandbox*). Le mettre dans votre gestionnaire de mots de passe.
2. Sur le serveur :
```bash
sudo nano /etc/ollisante/ollisante.env        # coller le contenu de deploy/ollisante.env.example, puis remplir
sudo chown root:ollisante /etc/ollisante/ollisante.env
sudo chmod 640 /etc/ollisante/ollisante.env
ls -l /etc/ollisante/ollisante.env             # doit afficher -rw-r----- root ollisante
```
3. Pour `JWT_SECRET` : `openssl rand -base64 48`. Pour `DB_PASSWORD` : le mot de passe de l'étape 3.
4. N'utiliser `nano` (et non `echo "..." >>`) pour que le jeton ne reste pas dans l'historique des commandes.
5. **Sandbox et production sont deux jetons différents** (et deux adresses différentes). Ne jamais mettre le jeton de production
   avant la fin des tests ; le changer = modifier ce fichier puis `sudo systemctl restart ollisante`.
6. Si un jeton a été exposé (chat, capture, dépôt) : le **révoquer** dans le tableau de bord et en créer un nouveau.

## 6. Déployer l'application
Sur votre ordinateur (JDK 17 + Maven) :
```bash
mvn -DskipTests package
scp target/ollisante-backend-0.1.0-SNAPSHOT.jar deploy@IP_DU_VPS:/tmp/ollisante-backend.jar
```
Sur le serveur :
```bash
sudo mv /tmp/ollisante-backend.jar /opt/ollisante/ && sudo chown ollisante:ollisante /opt/ollisante/ollisante-backend.jar
sudo cp deploy/ollisante.service /etc/systemd/system/ollisante.service     # fichier fourni dans ce dépôt
sudo systemctl daemon-reload && sudo systemctl enable --now ollisante
sudo journalctl -u ollisante -f                                             # suivre les journaux (Ctrl+C pour quitter)
```
Au premier démarrage, Flyway crée les tables. Test local : `curl http://127.0.0.1:8088/actuator/health`.

## 7. Nginx et HTTPS
```bash
sudo cp deploy/nginx-api.ollisante.com.conf /etc/nginx/sites-available/api.ollisante.com
sudo ln -s /etc/nginx/sites-available/api.ollisante.com /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.ollisante.com          # certificat HTTPS gratuit, renouvelé automatiquement
curl https://api.ollisante.com/actuator/health     # doit répondre {"status":"UP"}
```

## 8. Côté pawaPay
Dans le tableau de bord (environnement sandbox), renseigner l'**URL de rappel** (callback). Je vous fournirai l'adresse exacte
dès que la fonction de paiement sera livrée (elle sera de la forme `https://api.ollisante.com/api/v1/payments/pawapay/...`).
L'ancienne adresse `api.ollisante.com/ollisante/anonyme/retourPawaiPay` appartient à l'ancien serveur : elle ne sera plus utilisée.

## 9. Sauvegardes et hygiène
- Sauvegarde quotidienne : `pg_dump` chiffré vers un stockage externe, avec un test de restauration régulier.
- Mises à jour de sécurité : `sudo apt update && sudo apt upgrade` (au moins chaque mois).
- Ne jamais copier de vraies données patients sur un ordinateur de développement.
