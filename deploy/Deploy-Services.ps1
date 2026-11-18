param([string]$Project='hestia-514901',[Parameter(Mandatory)][string]$Region,[Parameter(Mandatory)][string]$ApiImage,[Parameter(Mandatory)][string]$WorkerImage,
 [Parameter(Mandatory)][string]$WorkerUrl,[Parameter(Mandatory)][string]$SupabaseUrl,[Parameter(Mandatory)][string]$PublishableKey,[Parameter(Mandatory)][string]$WebClientId,
 [Parameter(Mandatory)][int]$GoogleSecretVersion,[Parameter(Mandatory)][int]$DatabaseSecretVersion,[switch]$Execute)
$ErrorActionPreference='Stop'
if(-not $Execute){Write-Output 'PLAN ONLY: deploy public JWT API/private IAM worker, authenticated push subscription, daily watch renewal and six-hour reconciliation. No deployment performed. Review all arguments and rerun with -Execute only after authorization.';return}
function G { & gcloud @args; if($LASTEXITCODE -ne 0){throw 'gcloud failed'} }
$projectNumber=(& gcloud projects describe $Project --format='value(projectNumber)').Trim()
if($LASTEXITCODE -ne 0){throw 'Cannot verify project number'}
$expectedWorkerUrl="https://hestia-gmail-worker-$projectNumber.$Region.run.app"
if($WorkerUrl.TrimEnd('/') -ne $expectedWorkerUrl){throw 'WorkerUrl must match the deterministic HTTPS URL of this project and region'}
$envFile=Join-Path ([IO.Path]::GetTempPath()) ('hestia-public-env-'+[guid]::NewGuid()+'.json')
$values=@{GCP_PROJECT_ID=$Project;GMAIL_TOPIC="projects/$Project/topics/hestia-gmail-updates";GMAIL_SUBSCRIPTION="projects/$Project/subscriptions/hestia-gmail-worker";WORKER_AUDIENCE=$WorkerUrl.TrimEnd('/');PUBSUB_INVOKER_EMAIL="hestia-pubsub-invoker@$Project.iam.gserviceaccount.com";SCHEDULER_INVOKER_EMAIL="hestia-scheduler-invoker@$Project.iam.gserviceaccount.com";SUPABASE_URL=$SupabaseUrl;SUPABASE_PUBLISHABLE_KEY=$PublishableKey;GOOGLE_WEB_CLIENT_ID=$WebClientId;KMS_KEY_NAME="projects/$Project/locations/$Region/keyRings/hestia/cryptoKeys/gmail-refresh";FCM_PROJECT_ID=$Project;OAUTH_TESTING_MODE='true'}
$values|ConvertTo-Json|Set-Content -LiteralPath $envFile -Encoding utf8
try{
 foreach($kind in @('worker','api')){
  $image=if($kind -eq 'worker'){$WorkerImage}else{$ApiImage};$access=if($kind -eq 'worker'){'--no-allow-unauthenticated'}else{'--allow-unauthenticated'}
  G run deploy "hestia-gmail-$kind" --project $Project --region $Region --image $image --service-account="hestia-$kind@$Project.iam.gserviceaccount.com" $access --env-vars-file=$envFile --set-secrets="GOOGLE_WEB_CLIENT_SECRET=hestia-google-web-secret:$GoogleSecretVersion,DATABASE_URL=hestia-database-url:$DatabaseSecretVersion" --concurrency=8 --max-instances=3 --min-instances=0 --timeout=600 --memory=512Mi
 }
 foreach($name in @('hestia-pubsub-invoker','hestia-scheduler-invoker')) {G run services add-iam-policy-binding hestia-gmail-worker --project $Project --region $Region --member="serviceAccount:$name@$Project.iam.gserviceaccount.com" --role=roles/run.invoker}
 G pubsub subscriptions create hestia-gmail-worker --project $Project --topic=hestia-gmail-updates --push-endpoint="$WorkerUrl/webhooks/pubsub/gmail" --push-auth-service-account="hestia-pubsub-invoker@$Project.iam.gserviceaccount.com" --push-auth-token-audience=$WorkerUrl --ack-deadline=600 --min-retry-delay=10s --max-retry-delay=600s --message-retention-duration=7d
 G scheduler jobs create http hestia-gmail-renew --project $Project --location $Region --schedule='0 3 * * *' --time-zone=Etc/UTC --uri="$WorkerUrl/internal/gmail/renew-watches" --http-method=POST --oidc-service-account-email="hestia-scheduler-invoker@$Project.iam.gserviceaccount.com" --oidc-token-audience=$WorkerUrl --attempt-deadline=1800s
 G scheduler jobs create http hestia-gmail-reconcile --project $Project --location $Region --schedule='0 */6 * * *' --time-zone=Etc/UTC --uri="$WorkerUrl/internal/gmail/reconcile" --http-method=POST --oidc-service-account-email="hestia-scheduler-invoker@$Project.iam.gserviceaccount.com" --oidc-token-audience=$WorkerUrl --attempt-deadline=1800s
}finally{Remove-Item -LiteralPath $envFile -ErrorAction SilentlyContinue}
