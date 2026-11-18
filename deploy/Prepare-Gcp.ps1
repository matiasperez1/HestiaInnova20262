param([string]$Project='hestia-514901',[Parameter(Mandatory)][string]$Region,[switch]$Execute)
$ErrorActionPreference='Stop'
if(-not $Execute){Write-Output "PLAN ONLY: enable APIs; create four service identities, topic, KMS symmetric key and EMPTY secret containers in $Project / $Region. This incurs cloud costs when executed. Review docs/ETAPA_2C_GMAIL_AUTOMATICO.md; rerun with -Execute only after authorization.";return}
function G { & gcloud @args; if($LASTEXITCODE -ne 0){throw 'gcloud failed; inspect resources before retrying'} }
G services enable run.googleapis.com pubsub.googleapis.com cloudscheduler.googleapis.com cloudkms.googleapis.com secretmanager.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com gmail.googleapis.com fcm.googleapis.com --project $Project
G beta services identity create --service=pubsub.googleapis.com --project $Project
$number=(& gcloud projects describe $Project --format='value(projectNumber)').Trim()
foreach($name in @('hestia-api','hestia-worker','hestia-pubsub-invoker','hestia-scheduler-invoker')) {G iam service-accounts create $name --project $Project --display-name $name}
G pubsub topics create hestia-gmail-updates --project $Project
G pubsub topics add-iam-policy-binding hestia-gmail-updates --project $Project --member='serviceAccount:gmail-api-push@system.gserviceaccount.com' --role=roles/pubsub.publisher
G kms keyrings create hestia --location $Region --project $Project
G kms keys create gmail-refresh --keyring hestia --location $Region --purpose=encryption --default-algorithm=google-symmetric-encryption --project $Project
foreach($name in @('hestia-google-web-secret','hestia-database-url')) {G secrets create $name --replication-policy=automatic --project $Project}
foreach($name in @('hestia-api','hestia-worker')) {
 $member="serviceAccount:$name@$Project.iam.gserviceaccount.com"
 G kms keys add-iam-policy-binding gmail-refresh --keyring hestia --location $Region --project $Project --member=$member --role=roles/cloudkms.cryptoKeyEncrypterDecrypter
 foreach($secret in @('hestia-google-web-secret','hestia-database-url')) {G secrets add-iam-policy-binding $secret --project $Project --member=$member --role=roles/secretmanager.secretAccessor}
}
G pubsub topics add-iam-policy-binding hestia-gmail-updates --project $Project --member="serviceAccount:hestia-api@$Project.iam.gserviceaccount.com" --role=roles/pubsub.publisher
G iam service-accounts add-iam-policy-binding "hestia-pubsub-invoker@$Project.iam.gserviceaccount.com" --project $Project --member="serviceAccount:service-$number@gcp-sa-pubsub.iam.gserviceaccount.com" --role=roles/iam.serviceAccountTokenCreator
G projects add-iam-policy-binding $Project --member="serviceAccount:hestia-worker@$Project.iam.gserviceaccount.com" --role=roles/firebasecloudmessaging.admin
Write-Output 'Resources prepared. Add secret VERSIONS securely using local files outside the repository. This script intentionally fails if a resource already exists: inspect and reuse instead of overwriting.'
