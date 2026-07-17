export function clearWithdrawalSecrets(credentials) {
  credentials.tronPrivateKey = ''
  credentials.ethPrivateKey = ''
  credentials.totpCode = ''
}
