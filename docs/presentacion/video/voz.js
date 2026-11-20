// Narración por escena: la clave es el título del subtítulo o de la portada (grabar.js).
module.exports = {
  // 1 · Camino feliz
  'El camino feliz': 'SmartOps: el camino feliz. Seguimos las operaciones desde que llegan hasta que quedan conciliadas y auditadas.',
  'Bienvenidos a SmartOps': 'SmartOps es una plataforma que recibe, valida, procesa, concilia y audita operaciones bancarias, por API y en lotes masivos.',
  'El tablero operativo': 'Entra el operador. El tablero resume las operaciones registradas, el monto procesado, los rechazos y la tasa de conciliación.',
  'Registrar una transferencia': 'Primero, una operación en línea. El operador registra una transferencia entre dos cuentas. La clave de idempotencia identifica la operación de forma única.',
  'Procesada al instante': 'La operación se valida, se debita el origen, se acredita el destino y queda procesada, todo en una sola transacción.',
  'Un reintento no duplica': 'Ahora simulamos un reintento: el mismo envío, con la misma clave. SmartOps reconoce la operación y devuelve la original, sin debitar dos veces.',
  'Llega un archivo de cinco mil operaciones': 'Pero el volumen grande llega en archivos. El operador carga un lote de cinco mil operaciones, con algunos registros defectuosos a propósito.',
  'Spring Batch lo procesa por bloques': 'Spring Batch valida y carga el archivo por bloques de quinientos registros, y luego contabiliza cada operación válida. La pantalla se actualiza sola.',
  'Nada se pierde': 'Lote completado. Cada registro leído quedó procesado o rechazado: nada se pierde, y los registros malos no detuvieron el lote.',
  'Cada rechazo con su motivo': 'Cada rechazo tiene su línea, su código y su detalle: referencias duplicadas, cuentas inexistentes o bloqueadas, montos o fechas inválidas.',
  'El extracto del banco': 'Del otro lado está el extracto externo. El operador lo carga para comparar lo que SmartOps procesó con lo que reporta el banco.',
  'Conciliación automática': 'El motor empareja por referencia y clasifica cada una: conciliada, con diferencia de monto, o faltante en uno de los dos lados.',
  'Lo que requiere acción': 'Las diferencias aparecen primero, con el monto interno, el externo y la diferencia. Las operaciones que coincidieron quedaron marcadas como conciliadas.',
  'Todo queda auditado': 'Por último entra la auditora. Cada operación, cada archivo, cada lote y cada conciliación quedaron registrados con su usuario, su fecha y su detalle. Las peticiones del API llevan además su identificador de correlación.',
  'Un rastro que no se borra': 'Y ese rastro no se puede modificar ni borrar, ni siquiera desde la base de datos. Eso es SmartOps. Gracias por su tiempo.',
};
