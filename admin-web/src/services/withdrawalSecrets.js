export function clearWithdrawalSecrets(credentials) {
  credentials.tronPrivateKey = ''
  credentials.ethPrivateKey = ''
  credentials.bepPrivateKey = ''
  credentials.totpCode = ''
}
