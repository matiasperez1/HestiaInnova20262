export const scenarios = [
  {
    id: "legitimate",
    label: "Institución: correo legítimo",
    subject: "Información de tu institución de prueba",
    body: "Tu estado de cuenta ya está disponible. Visita el sitio que utilizas habitualmente.",
  },
  {
    id: "high-risk",
    label: "Solicitud de claves bancarias",
    subject: "Banco de prueba: urgente",
    body: "Ingresa tu clave bancaria para verificar tu cuenta en http://short.example.invalid/acceso",
  },
  {
    id: "account-block",
    label: "Amenaza de bloqueo",
    subject: "Bloquearemos tu cuenta hoy mismo",
    body: "Confirma tu código personal en http://acceso.example.invalid",
  },
  {
    id: "shortened-link",
    label: "Enlace acortado",
    subject: "Urgente: enlace acortado de prueba",
    body: "Revisa http://acortador.example.invalid/r. Es un destino sintético y no se abre.",
  },
  {
    id: "reply-mismatch",
    label: "From y Reply-To diferentes",
    subject: "Confirma tu código personal",
    body: "Información de prueba",
    replyTo: "respuesta@otro.example.invalid",
  },
  {
    id: "urgent-payment",
    label: "Pago urgente",
    subject: "Urgente: último plazo",
    body: "Paga para liberar tu premio hoy mismo.",
  },
  {
    id: "isolated-signal",
    label: "Legítimo con señal aislada",
    subject: "Urgente: cambio de horario",
    body: "La reunión familiar de prueba será mañana.",
  },
  {
    id: "limited-content",
    label: "Contenido limitado",
    subject: "Mensaje con adjunto de prueba",
    body: "",
  },
] as const;
export function fixture(scenario: string, id: string, now: Date) {
  const s = scenarios.find((s) => s.id === scenario);
  if (!s) throw new Error("UNKNOWN_SCENARIO");
  // All domains are reserved. No HTTP client in this environment opens message links.
  return {
    id,
    internalDate: String(now.getTime()),
    labelIds: ["INBOX"],
    payload: {
      mimeType: "multipart/mixed",
      headers: [
        { name: "From", value: "Institución Demo <avisos@example.invalid>" },
        {
          name: "Reply-To",
          value: "replyTo" in s ? s.replyTo : "avisos@example.invalid",
        },
        { name: "Subject", value: "[DEMO LOCAL] " + s.subject },
      ],
      parts:
        s.id === "limited-content"
          ? [
              {
                mimeType: "application/pdf",
                filename: "ejemplo-sintetico.pdf",
                body: { attachmentId: "not-downloaded" },
              },
            ]
          : [
              {
                mimeType: "text/plain",
                body: { data: Buffer.from(s.body).toString("base64url") },
              },
            ],
    },
  };
}
