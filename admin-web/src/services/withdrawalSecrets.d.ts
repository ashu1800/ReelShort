export type WithdrawalCredentials = {
  tronPrivateKey: string
  ethPrivateKey: string
  bepPrivateKey: string
  totpCode: string
}

export function clearWithdrawalSecrets(credentials: WithdrawalCredentials): void
