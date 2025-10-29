from datetime import datetime, timedelta
import sqlite3
import base64

DB_PATH = 'templates/static/db/downez.db'
NECESARIO = 100.0 # Treshold o mural a superar de creditos acumulados para descuento por creditos consumidos.
ACUMULADO_DESCUENTO = 0.20 # descuento del 20%


# [IDF-0148] Función para retornar el conector de sql para cada clase entidad.
def get_connection():
    """
    Retorna una conexión SQLite con el parámetro check_same_thread desactivado.
    Esto permite usar la conexión en diferentes hilos, útil en entornos multihilo.
    """
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    return conn

# [IDF-0215] Función que retorna los registros según el tipo de tabla.
def getTable(tipo):
    """
    Retorna todos los registros de la tabla indicada por 'tipo' y sus etiquetas de columnas.

    - Si el tipo es 'contenidos', excluye el campo Archivo_bytes.
    - Para las demás tablas, retorna todos los campos (*).

    Retorna:
        tuple: (labels, rows)
            - labels: lista de nombres de columnas
            - rows: lista de tuplas con los datos
    """
    conn = get_connection()
    cursor = conn.cursor()
    tipo = tipo.lower()

    if tipo == 'contenidos':
        query = """
            SELECT 
                id,
                nombre_contenido,
                autor,
                precio,
                extension,
                categoria_id,
                rating,
                descripcion,
                tipo_contenido,
                downloaded,
                estado,
                id_promocion
            FROM contenidos
        """
    else:
        query = f"SELECT * FROM {tipo}"

    try:
        cursor.execute(query)
        rows = cursor.fetchall()
        labels = [description[0] for description in cursor.description]
        return {'labels':labels, 'rows':rows}
    except sqlite3.Error as e:
        print(f"Error al obtener tabla '{tipo}':", e)
        return [], []
    finally:
        conn.close()

class E_Usuarios:
    def __init__(self):
        # Establece la conexión y el cursor para ejecutar queries SQL
        self.conn = get_connection()
        self.cursor = self.conn.cursor()

    # [IDF-0037] Registrar a un cliente a ex-cliente en la tabla usuarios.
    def registrar_Excliente(self, idU):
        """
        Actualiza el estado de cuenta del usuario a 'ex-cliente' dado su ID.
        """
        query = "UPDATE usuarios SET estado_cuenta = 'ex-cliente' WHERE id = ?"
        self.cursor.execute(query, (idU,))
        self.conn.commit()

    # [IDF-0038] Verifica en la tabla usuarios si el username y password son correctos, siempre y cuando sean clientes o un administrador.
    def verificarLogin(self, username, password):
        """
        Verifica credenciales del usuario. Solo permite acceso a cuentas activas (cliente o administrador).

        Retorna:
            tuple: (role, id) si las credenciales son válidas; (None, None) en caso contrario.
        """

        query = """
        SELECT 
                role, id 
            FROM 
                usuarios 
            WHERE 
                username = ? AND pswd = ? AND (estado_cuenta = 'cliente' OR estado_cuenta = 'administrador')
        """
        self.cursor.execute(query, (username, password))
        result = self.cursor.fetchone()
        if result:
            return result[0], result[1]  # tipo (Administrador/Cliente), id_usuario
        return None, None
    
    # [IDF-0039] Obtiene los datos de un usuario por id de la tabla usuarios.
    def obtenerUser(self, id_usuario):
        """
        Retorna información del usuario dado su ID.

        Retorna:
            dict: contiene username, email, saldo, full name, y estado de cuenta. None si no existe.
        """        
        query="SELECT username, email, saldo, estado_cuenta,nombre,apellido1,apellido2 FROM usuarios WHERE id = ?"
        self.cursor.execute(query, (id_usuario,))
        resultado = self.cursor.fetchone()

        if resultado:
            return {
                "username": resultado[0],
                "email": resultado[1],  
                "saldo": resultado[2],
                "estado":resultado[3],
                "fullname":resultado[4] + " " + resultado[5] + " " + resultado[6]
            }
        return None

    # [IDF-0040] Obtiene el saldo de un usuario por id de la tabla usuarios.
    def obtenerSaldo(self, id):
        """
        Consulta y retorna el saldo de un usuario específico.

        Retorna:
            float: saldo del usuario, o None si no se encuentra.
        """        
        query = "SELECT saldo FROM usuarios WHERE id = ?"
        self.cursor.execute(query, (id,))
        result = self.cursor.fetchone()
        print(result)
        if result:
            return result[0]
        return None

    # [IDF-0041] Actualiza el saldo de un usuario por id de la tabla usuarios.
    def actualizarSaldo(self,id, cantidad, recarga=False):
        """
        Suma una cantidad al saldo actual del usuario.

        Parámetros:
            id (int): ID del usuario.
            cantidad (float): Monto a sumar.
            recarga (bool): define si es una recarga o compra.
        """   
        if not recarga:
            query = "UPDATE usuarios SET saldo = saldo + ?, acumulado = acumulado + ? WHERE id = ?"
            self.cursor.execute(query, (cantidad, -cantidad, id))
        else:
            query = "UPDATE usuarios SET saldo = saldo + ? WHERE id = ?"
            self.cursor.execute(query, (cantidad, id))
        self.conn.commit()

    # [IDF-0042] Valida los datos un usuario por id de la tabla usuarios, para verificar si existe y sea una cuenta activa.
    def validarDatos(self, username):
        """
        Verifica si un nombre de usuario ya está registrado como cuenta activa.

        Retorna:
            bool: True si NO existe (puede registrarse), False si ya está en uso.
        """        
        query = "SELECT id FROM usuarios WHERE username = ? AND (estado_cuenta = 'cliente' OR estado_cuenta = 'administrador')"
        self.cursor.execute(query, (username,))
        result = self.cursor.fetchone()
        return result is None # si es none, es porque no se encontro, por ende no existe ese usuario a registrar :D
    
    # [IDF-0043] Verifica en la tabla usuarios si un cliente existe por id y estado de cuenta.
    def UsuarioExiste(self, username, idU):
        """
        Comprueba si ya existe otro usuario con el mismo username y diferente ID.

        Retorna:
            int: -1 si no existe duplicado; ID del duplicado si existe.
        """        
        query = "SELECT id FROM usuarios WHERE username = ? AND id != ? AND estado_cuenta = 'cliente'"
        self.cursor.execute(query, (username,idU))
        result = self.cursor.fetchone()
        return -1 if result is None else result[0]
    
    # [IDF-0044] Registra un usuario a la tabla usuarios.
    def registrarUsuario(self, data):
        """
        Inserta un nuevo usuario en la tabla.

        Parámetros:
            un diccionario que contiene:
                username (str): Nombre de usuario.
                password (str): Contraseña.
                email (str): Correo electrónico.
                nombre (str): nombre del usuario.
                apellido_paterno (str): apelledio paterno del usuario.
                apellido_materno (str): apelledio materno del usuario.
        """
        username = data.get("username")
        password = data.get("password")
        email = data.get("email")
        nombre = data.get("nombre")
        apellido1 = data.get("apellido_paterno")
        apellido2 = data.get("apellido_materno")
        query = "INSERT INTO usuarios (username, pswd, email,nombre, apellido1,apellido2) VALUES (?,?,?,?,?,?)"
        self.cursor.execute(query, (username, password, email,nombre,apellido1,apellido2))
        self.conn.commit()
        return self.cursor.lastrowid
    
    # [IDF-0045] Retorna información de varios usuarios por coincidencia de id's o usernames, solo para administradores.
    def buscar_info_usuarios(self, query):
        """
        Busca usuarios cuyos IDs o usernames contengan el texto dado.

        Parámetros:
            query (str): Texto de búsqueda.

        Retorna:
            list: Lista de diccionarios con datos de usuarios coincidentes.
        """        
        q_like = f"%{query.lower()}%"
        sql = "SELECT id, username, email, estado_cuenta FROM usuarios WHERE CAST(id AS TEXT) LIKE ? OR LOWER(username) LIKE ?"
        self.cursor.execute(sql, (q_like,q_like))
        result = self.cursor.fetchall()

        lista = []
        for row in result:
            lista.append({
                "id": row[0],
                "title": row[1],
                "author": row[2],
                "type": row[3],
            })
        return lista
    
    # [IDF-0198] Verifica si un cliente tiene suficiente acumulado para poder aprobar el descuento de creditos consumidos, en la tabla usuarios.
    def verificarAcumulado(self,idU):
        """
       Verifica si el acumulado actual del usuario cumple con las condiciones.

        Parámetros:
            idU (int): ID del usuario.
        """           
        query = "SELECT acumulado FROM usuarios WHERE id = ? AND estado_cuenta = 'cliente'"
        self.cursor.execute(query, (idU,))
        result = self.cursor.fetchone()
        if result is None:
            return False
        return result[0] > NECESARIO
    
    # [IDF-0199] Reinicia el acumulado de un cliente, en la tabla usuarios.
    def consumirAcumulado(self,idU):
        """
        Consume el acumulado de saldo actual del usuario.

        Parámetros:
            idU (int): ID del usuario.
        """   
        query = "UPDATE usuarios SET acumulado = 0.0 WHERE id = ?"
        self.cursor.execute(query, (idU,))
        self.conn.commit()
    
    # [IDF-0200] Consulta a la tabla usuarios y las descargas para hacer una lista de los clientes con más descargas.
    def obtenerRankingUsuariosPorDescargas(self):
        """
        Retorna una lista de usuarios ordenados por la cantidad total de descargas (de mayor a menor).
        Incluye: id del usuario, username, total de descargas y estado de cuenta.
        """
        query = """
            SELECT 
                u.id,
                u.username,
                u.estado_cuenta,
                SUM(d.downloaded) AS total_descargas
            FROM descarga d
            JOIN usuarios u ON d.id_usuario = u.id
            WHERE u.estado_cuenta != 'administrador'
            GROUP BY u.id
            ORDER BY total_descargas DESC
        """
        self.cursor.execute(query)
        resultados = self.cursor.fetchall()

        ranking = []
        for fila in resultados:
            id_usuario, username, estado, total = fila
            ranking.append({
                "id": id_usuario,
                "username": username,
                "estado_cuenta": estado,
                "total_descargas": total
            })

        return ranking

class E_Compras:
    def __init__(self):
        self.conn = get_connection()
        self.cursor = self.conn.cursor()

    # [IDF-0046] Verifica su un usuario compro cierto contenido, se valida por id's en la tabla compras.
    def verificarContenido(self, idu, idc):
        """
        Verifica si un usuario ya ha comprado cierto contenido.

        Parámetros:
            idu (int): ID del usuario.
            idc (int): ID del contenido.

        Retorna:
            bool: True si ya compró el contenido, False si no.
        """
        query = "SELECT id_contenido, id_usuario FROM compras WHERE id_contenido = ? AND id_usuario = ?"
        self.cursor.execute(query, (idc,idu,))
        result = self.cursor.fetchone()
        #print(result)
        if(result is None): return False
        return True
    
    # [IDF-0047] Registra una compra de un cliente y retorna el id de la compra.
    def registrarCompra(self, idU, idC,precio, type_trans="compra"):
        """
        Registra una nueva compra en la tabla 'compras'.

        Parámetros:
            idU (int): ID del usuario que compra.
            idC (int): ID del contenido.
            precio (float): Precio del contenido.
            type_trans (str): Tipo de transacción ('compra' por defecto).

        Retorna:
            int: ID de la compra registrada.
        """
        query = "INSERT INTO compras (id_usuario,id_contenido, precio, fecha, tipo_compra) VALUES (?, ?, ?, ?,?)"
        fecha = str(datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        self.cursor.execute(query, (idU, idC, precio, fecha,type_trans))
        transaccion_id = self.cursor.lastrowid  # obtiene el ID insertado
        self.conn.commit()
        return transaccion_id

    # # [IDF-0048] Retorna las compras de un cliente de la tabla compras.
    # def obtenerDescargasCliente(self, id_usuario):
    #     query = """
    #         SELECT 
    #             c.nombre_contenido, 
    #             c.rating, 
    #             c.tipo_contenido, 
    #             c.autor, 
    #             c.id, 
    #             uc.tipo_compra,
    #             CASE 
    #                 WHEN uc.tipo_compra = 'regalo' THEN u.username 
    #                 ELSE NULL 
    #             END AS destinatario_username
    #         FROM compras uc
    #         JOIN contenidos c ON uc.id_contenido = c.id
    #         LEFT JOIN regalos r ON uc.tipo_compra = 'regalo' AND uc.id = r.id_regalo
    #         LEFT JOIN usuarios u ON r.id_destinatario = u.id
    #         WHERE uc.id_usuario = ?
    #     """
    #     self.cursor.execute(query, (id_usuario,))
    #     results = self.cursor.fetchall()
    #     lista = []
    #     for row in results:
    #         lista.append({
    #             "title": row[0],
    #             "rating": row[1],
    #             "type": row[2],
    #             "author": row[3],
    #             "id": row[4],
    #             "tipo_compra": row[5] + " al usuario " + row[6] if row[6] else row[5]
    #         })
    #     return lista

    # [IDF-0048] Retorna las compras de un cliente de la tabla compras, incluyendo el número de descargas.
    def obtenerDescargasCliente(self, id_usuario):
        """
        Retorna todas las compras realizadas por un usuario, incluyendo el número de descargas
        y si fue una compra directa o regalo.

        Parámetros:
            id_usuario (int): ID del usuario.

        Retorna:
            list[dict]: Lista de objetos con información del contenido, tipo de compra y descargas.
        """        
        query = """
            SELECT 
                c.nombre_contenido, 
                c.rating, 
                c.tipo_contenido, 
                c.autor, 
                c.id, 
                uc.tipo_compra,
                CASE 
                    WHEN uc.tipo_compra = 'regalo' THEN u.username 
                    ELSE NULL 
                END AS destinatario_username,
                IFNULL(d.downloaded, 0) AS num_descargas
            FROM compras uc
            JOIN contenidos c ON uc.id_contenido = c.id
            LEFT JOIN regalos r ON uc.tipo_compra = 'regalo' AND uc.id = r.id_regalo
            LEFT JOIN usuarios u ON r.id_destinatario = u.id
            LEFT JOIN descarga d ON d.id_usuario = uc.id_usuario AND d.id_contenido = c.id
            WHERE uc.id_usuario = ?
        """
        self.cursor.execute(query, (id_usuario,))
        results = self.cursor.fetchall()
        lista = []
        for row in results:
            lista.append({
                "title": row[0],
                "rating": round(row[1],2),
                "type": row[2],
                "author": row[3],
                "id": row[4],
                "tipo_compra": row[5] + " al usuario " + row[6] if row[6] else row[5],
                "descargas": row[7]
            })
        return lista

class E_Regalos(E_Compras):
    def __init__(self):
        super().__init__()

    # [IDF-0049] Registra un regalo en tabla compras y el destinatario en la tabla regalos.
    def registrarRegalo(self, idU, idC, precio, type_trans, id_des):
        """
        Registra una compra con tipo 'regalo' y asigna el contenido a un destinatario.

        Parámetros:
            idU (int): ID del usuario que envía el regalo.
            idC (int): ID del contenido.
            precio (float): Precio del contenido.
            type_trans (str): Tipo de transacción ('regalo').
            id_des (int): ID del destinatario del regalo.
        """        
        id_trans = self.registrarCompra(idU,idC,precio,type_trans)
        query = "INSERT INTO regalos (id_regalo, id_destinatario) VALUES (?, ?)"
        self.cursor.execute(query, (id_trans,id_des))
        self.conn.commit()

    # [IDF-0050] Verifica si el destinarario ya tiene el contenido.
    def verificarContenidoDestinatario(self, id_des, idc):
        """
        Verifica si el destinatario ya recibió ese contenido previamente como regalo.

        Parámetros:
            id_des (int): ID del destinatario.
            idc (int): ID del contenido.

        Retorna:
            bool: True si ya lo recibió, False si no.
        """        
        query = """
            SELECT 
                c.id
            FROM 
                compras c
            JOIN 
                regalos r ON c.id = r.id_regalo
            WHERE 
                c.id_contenido = ? AND r.id_destinatario = ?
        """
        self.cursor.execute(query, (idc, id_des))
        result = self.cursor.fetchone()
        return result is not None
    
class E_Puntuaciones:
    def __init__(self):
        self.conn = get_connection()
        self.cursor = self.conn.cursor()

    # [IDF-0051] Registra una puntuación en la tabla puntuaciones.
    def Registrar_Puntuacion(self, user_id, id_contenido, puntuacion):
        """
        Registra o actualiza la puntuación de un contenido hecha por un usuario.
        Luego recalcula y actualiza el promedio de puntuaciones en la tabla 'contenidos'.

        Parámetros:
            user_id (int): ID del cliente.
            id_contenido (int): ID del contenido.
            puntuacion (int): Puntuación entre 1 y 5.
        """
        if not self.Existe_Puntuacion(user_id,id_contenido):
            # Paso 1: Insertar nueva puntuación
            insert_query = "INSERT INTO puntuaciones (id_contenido, id_cliente, puntuacion) VALUES (?, ?, ?)"
            self.cursor.execute(insert_query, (id_contenido, user_id, puntuacion,))
        else:
            update_query = "UPDATE puntuaciones SET puntuacion = ? WHERE id_contenido = ? AND id_cliente = ?"
            self.cursor.execute(update_query, (puntuacion, id_contenido, user_id,))
        self.conn.commit()

        # Paso 2: Calcular el nuevo promedio de puntuaciones
        promedio_query = "SELECT AVG(puntuacion) FROM puntuaciones WHERE id_contenido = ?"
        self.cursor.execute(promedio_query, (id_contenido,))
        promedio = self.cursor.fetchone()[0]

        # Paso 3: Actualizar el campo 'rating' en la tabla contenidos
        update_query = "UPDATE contenidos SET rating = ? WHERE id = ?"
        self.cursor.execute(update_query, (promedio, id_contenido))
        self.conn.commit()
    
    # [IDF-0052] Verifica si ya existe una puntuación en la tabla puntuaciones.
    def Existe_Puntuacion(self, idU,idC):
        """
        Verifica si un usuario ya ha puntuado un contenido.

        Parámetros:
            idU (int): ID del usuario.
            idC (int): ID del contenido.

        Retorna:
            bool: True si ya existe una puntuación, False si no.
        """
        query = "SELECT * FROM puntuaciones WHERE id_cliente = ? AND id_contenido = ?"
        self.cursor.execute(query, (idU,idC,))
        result = self.cursor.fetchone()
        if(result is None): return False
        return True

class E_Notificaciones:
    def __init__(self):
        self.conn = get_connection()
        self.cursor = self.conn.cursor()

    # [IDF-0053] Registra una notificación en la tabla notificiones, y retorna un html de un resultado de un contenido como notifiación.
    def registrarNotificacionRegalo(self, idU, idC, msg):
        """
        Registra una notificación de regalo en la tabla 'notificaciones' para un usuario,
        incluyendo un enlace HTML al contenido regalado.

        Parámetros:
            idU (int): ID del usuario receptor.
            idC (int): ID del contenido.
            msg (str): Mensaje personalizado.
        """        
        query="SELECT nombre_contenido, id FROM contenidos WHERE id = ?"
        self.cursor.execute(query, (idC,))
        result = self.cursor.fetchone()
        full_msg = f"<strong>Titulo:<a href=item_view.html?id={result[1]}></strong> {result[0]}</a></p><p>{msg}"
        query = "INSERT INTO notificaciones (id_usuario, messagge) VALUES (?, ?)"
        self.cursor.execute(query, (idU, full_msg))
        self.conn.commit()
    
    # [IDF-0054] Registra una notificación en la tabla notificiones de tipo recarga.
    def registrarNotificacionRecarga(self, idU, msg):
        """
        Registra una notificación de recarga en la tabla 'notificaciones'.

        Parámetros:
            idU (int): ID del usuario.
            msg (str): Mensaje de notificación.
        """        
        query = "INSERT INTO notificaciones (id_usuario, messagge) VALUES (?, ?)"
        self.cursor.execute(query, (idU, msg))
        self.conn.commit()

    # [IDF-0055] Obtiene una lista de todas las notifiaciones para cierto cliente.
    def obtenerListaNotificaciones(self, idU):
        """
        Obtiene la lista de todas las notificaciones asociadas a un usuario.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            list[dict]: Lista de notificaciones con ID y mensaje.
        """        
        query = """
                SELECT 
                    n.id,
                    n.messagge
                FROM 
                    notificaciones n
                WHERE 
                    n.id_usuario = ?
            """
        self.cursor.execute(query, (idU,))
        result = self.cursor.fetchall()

        lista = [{"id_notificacion": row[0],
                    "messagge": row[1]} for row in result]
        return lista
    
    # [IDF-0056] Acepta una notifiación y la marca como leída, esta es eliminada de la tabla notificaciones.
    def aceptarNotificacion(self, id_noti):
        """
        Acepta o marca como leída una notificación eliminándola de la tabla 'notificaciones'.

        Parámetros:
            id_noti (int): ID de la notificación a eliminar.
        """
        query_delete = "DELETE FROM notificaciones WHERE id = ?"
        self.cursor.execute(query_delete, (id_noti,))
        self.conn.commit()
            
class E_Recargas:
    def __init__(self):
        self.conn = get_connection()
        self.cursor = self.conn.cursor()

    # [IDF-0057] Regista una solicitud de recarga de un cliente.
    def registrarSolicitud(self, monto, user_id):
        """
        Registra una nueva solicitud de recarga por parte de un cliente.

        Parámetros:
            monto (float): Monto a recargar.
            user_id (int): ID del usuario que solicita la recarga.
        """        
        fecha = str(datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        query = "INSERT INTO recargas (id_user, monto, fecha) VALUES (?, ?, ?)"
        self.cursor.execute(query, (user_id, monto, fecha))
        self.conn.commit()

    # [IDF-0058] Obtiene una lista de todas las peticiones de recargas de saldo, para el administrador.
    def obtenerListaPeticiones(self):
        """
        Obtiene la lista de solicitudes de recarga pendientes para revisión del administrador.

        Retorna:
            list[dict]: Lista de solicitudes con ID, usuario, monto, fecha y estado.
        """
        query = """
            SELECT 
                recargas.id, 
                usuarios.username, 
                recargas.monto, 
                recargas.fecha, 
                recargas.estado
            FROM 
                recargas
            JOIN 
                usuarios ON recargas.id_user = usuarios.id
            WHERE 
                recargas.estado = 'pendiente' AND usuarios.estado_cuenta = 'cliente'
        """
        self.cursor.execute(query)
        result = self.cursor.fetchall()

        lista = [{"id_recarga": row[0],
                "usuario": row[1],
                "monto": row[2],
                "fecha": row[3],
                "estado": row[4]} for row in result]

        return lista
    
    # [IDF-0059] Retorna información del historial de recargas de un cliente según su id.
    def obtenerRecargasCliente(self, idU):
        """
        Retorna el historial de recargas realizadas por un cliente.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            list[dict]: Lista de recargas con ID, monto, fecha y estado.
        """
        query = """
            SELECT 
                id,
                monto,
                fecha,
                estado
            FROM 
                recargas
            WHERE 
                id_user = ?
        """
        self.cursor.execute(query,(idU))
        result = self.cursor.fetchall()

        lista = [{"id_recarga": row[0],
                "monto": row[1],
                "fecha": row[2],
                "estado": row[3]} for row in result]

        return lista
    
    # [IDF-0060] El administrador prueba una recarga de un cliente.
    def aprobarRecarga(self, id_recarga):
        """
        Aprueba una solicitud de recarga pendiente, cambiando su estado a 'aprobada'.

        Parámetros:
            id_recarga (int): ID de la recarga a aprobar.

        Retorna:
            tuple: (id_user, monto) si fue exitosa, (None, None) si no.
        """
        query = "SELECT id_user, monto FROM recargas WHERE id = ? AND estado = 'pendiente'"
        self.cursor.execute(query, (id_recarga,))
        result = self.cursor.fetchone()
        if result:
            id_user, monto = result
            query_set = "UPDATE recargas SET estado = 'aprobada' WHERE id = ?"
            self.cursor.execute(query_set, (id_recarga,))
            self.conn.commit()
            return id_user, monto
        return None, None
    
class E_Promociones:
    def __init__(self):
        self.conn = get_connection()
        self.cursor = self.conn.cursor()

    # [IDF-0162] Se retornan todas las promociones disponibles de la tabla promociones.
    def obtenerPromociones(self):
        """
        Retorna todas las promociones disponibles desde la tabla 'promociones'.

        Retorna:
            list[dict]: Lista de promociones con ID, descuento, título y fecha de fin.
        """
        self.cursor.execute('''
            SELECT id, descuento, titulo_de_descuento,fecha_fin FROM promociones
        ''')
        
        promociones = self.cursor.fetchall()
        results = []
        for promo in promociones:
            results.append({
                "id": promo[0],
                "descuento": promo[1],
                "titulo_de_descuento": promo[2],
                "fecha_fin": promo[3]
            })

        return results
    
    # [IDF-0165] Retorna el descuento de la tabla promociones de cierto id de promoción.
    def ObtenerPromocion(self, idprom):
        """
        Retorna el descuento asociado a una promoción específica.

        Parámetros:
            idprom (int): ID de la promoción.

        Retorna:
            tuple: Descuento de la promoción o None si no existe.
        """
        self.cursor.execute("SELECT descuento FROM promociones WHERE id = ?", (idprom,))
        resultado_promo = self.cursor.fetchone()
        return resultado_promo

    # [IDF-0177] Se registra una nueva promocion a la tabla promociones.
    def agregarPromocion(self, data):
        """
        Registra una nueva promoción en la base de datos.

        Parámetros:
            data (dict): Diccionario con 'titulo', 'descuento' (en %) y 'dias' (duración).

        Retorna:
            bool: True si se insertó correctamente, False si hubo error.
        """        
        try:
            fecha_futura = datetime.now() + timedelta(days=data.get("dias"))
            fecha_str = fecha_futura.strftime("%Y-%m-%d")
            descuento = float(data.get("descuento"))/100.0
            query = "INSERT INTO promociones (titulo_de_descuento,descuento, fecha_fin) VALUES (?, ?, ?)"
            self.cursor.execute(query, (data.get("titulo"), descuento, fecha_str))
            self.conn.commit()
            return True
        except:
            return False
        
    def limpiarPromocionesVencidas(self):
        """
        Elimina promociones cuya fecha de fin ya pasó y actualiza los contenidos asociados
        para que no tengan esa promoción (id_promocion = NULL).

        Retorna: None
        """
        try:
            hoy = datetime.now().strftime("%Y-%m-%d")
            # Obtener promociones vencidas
            self.cursor.execute("SELECT id FROM promociones WHERE fecha_fin < ?", (hoy,))
            promos_vencidas = self.cursor.fetchall()

            for (id_prom,) in promos_vencidas:
                # Quitar la promoción de los contenidos
                self.cursor.execute("UPDATE contenidos SET id_promocion = NULL WHERE id_promocion = ?", (id_prom,))
                # Eliminar la promoción vencida
                self.cursor.execute("DELETE FROM promociones WHERE id = ?", (id_prom,))

            self.conn.commit()
            return True
        except Exception as e:
            print(f"Error al limpiar promociones vencidas: {e}")
            return False

class E_Contenidos:
    def __init__(self):
        self.conn = get_connection()
        self.cursor = self.conn.cursor()

    # [IDF-0061] Registra un contenido en la tabla contenidos.
    def registrarContenido(self, data):
        """
        Registra un nuevo contenido en la base de datos.

        Parámetros:
            data (dict): Contiene 'src', 'title', 'author', 'price', 
                        'extension', 'category', 'rating', 'description', 'type'.

        Retorna:
            None
        """        
        query = """
            INSERT INTO contenidos (
                Archivo_bytes,
                nombre_contenido,
                autor,
                precio,
                extension,
                categoria_id,
                rating,
                descripcion,
                tipo_contenido
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        self.cursor.execute(query, (
            data["src"],
            data["title"],
            data["author"],
            float(data["price"]),
            data["extension"],
            data["category"],
            float(data["rating"]),
            data["description"],
            data["type"]
        ))
        self.conn.commit()

    # [IDF-0062] Actualiza un contenido existente en la tabla contenidos.
    def actualizarContenido(self, data):
        """
        Actualiza campos específicos de un contenido por su ID.

        Parámetros:
            data (dict): Debe incluir 'id' y al menos un campo editable.

        Retorna:
            None
        """
        campos_validos = {
            "Archivo_bytes": data.get("src"),
            "nombre_contenido": data.get("title"),
            "autor": data.get("author"),
            "precio": float(data["price"]) if data.get("price") is not None else None,
            "extension": data.get("extension"),
            "rating": float(data["rating"]) if data.get("rating") is not None else None,
            "descripcion": data.get("description"),
            "tipo_contenido": data.get("type")
        }

        # Filtrar solo campos que no son None
        columnas = []
        valores = []

        for campo, valor in campos_validos.items():
            if valor is not None:
                columnas.append(f"{campo} = ?")
                valores.append(valor)

        if not columnas:
            return  # Nada que actualizar

        query = f"""
            UPDATE contenidos SET
                {', '.join(columnas)}
            WHERE id = ?
        """

        valores.append(data["id"])
        self.cursor.execute(query, tuple(valores))
        self.conn.commit()

    # [IDF-0063] Obtiene contenidos con toda su información y con la opcion de retornar en orden del mas descargado al menos decargador.
    def obtenerContenidos(self, top=None):
        """
        Retorna todos los contenidos activos, con detalles y precio ajustado si tiene promoción.

        Parámetros:
            top (bool): Si es True, ordena por más descargados.

        Retorna:
            list: Lista de contenidos en formato dict.
        """
        query = """
            SELECT 
                c.id, c.Archivo_bytes, c.nombre_contenido, c.autor, c.precio, c.descripcion,
                c.rating, c.tipo_contenido, cat.ruta, c.extension, c.downloaded,
                c.id_promocion
            FROM contenidos c
            LEFT JOIN categorias cat ON c.categoria_id = cat.id
            WHERE c.estado = 'activo'
        """
        if top == 'descargas':
            query += " ORDER BY c.downloaded DESC"
        elif top == 'puntuaciones':
            query += " ORDER BY c.rating DESC"
    

        self.cursor.execute(query)
        result = self.cursor.fetchall()
        lista = []

        for row in result:
            (id_, src_bin, title, author, price, desc, rating,
            tipo, ruta_categoria, ext, down, id_prom) = row

            # Generar data URL para el contenido
            data_url = C_Contenidos._generar_data_url(src_bin, tipo, ext)

            # Verificar si tiene promoción y modificar el precio si es necesario
            if id_prom is not None:
                self.cursor.execute("SELECT titulo_de_descuento, descuento FROM promociones WHERE id = ?", (id_prom,))
                promo_row = self.cursor.fetchone()
                if promo_row:
                    titulo_prom, descuento = promo_row
                    precio_final = round(float(price) * (1 - descuento), 2)
                    price = f"<span style='color:green;'>{precio_final}</span> <s>{price}</s> ({titulo_prom})"

            lista.append({
                "id": id_,
                "src": data_url,
                "title": title,
                "author": author,
                "price": price,
                "description": desc,
                "rating": round(rating,2),
                "type": tipo,
                "category": ruta_categoria,
                "downloaded": down
            })

        return lista

    # [IDF-0064] Retorna contenidos para cierta coincidencia con un query y con filtros de la tabla contenidos.
    def Buscar_info(self, query="", filters=None, admi=False):
        """
        Realiza búsqueda de contenidos con filtros de tipo, autor, id o categoría.
        Si no hay filtros, busca solo por nombre_contenido.

        Parámetros:
            query (str): Texto a buscar.
            filters (list): Lista de filtros marcados (tipo, autor, id, categorias...).
            admi (bool): Si es True, también muestra contenidos inactivos.

        Retorna:
            list: Lista de resultados (id, título, autor, tipo, categoría).
        """
        if filters is None:
            filters = []

        # JOIN para acceder al nombre de categoría
        sql = """
            SELECT c.id, c.nombre_contenido, c.autor, c.tipo_contenido, c.estado, cat.nombre
            FROM contenidos c
            LEFT JOIN categorias cat ON c.categoria_id = cat.id
            WHERE 1=1
        """

        if not admi:
            sql += " AND c.estado='activo'"

        params = []
        filters_lower = [f.lower() for f in filters]

        # Filtro por tipo de contenido
        tipos = [f for f in filters_lower if f in ["imagen", "video", "audio"]]
        if tipos:
            placeholders = ", ".join(["?"] * len(tipos))
            sql += f" AND LOWER(c.tipo_contenido) IN ({placeholders})"
            params.extend(tipos)

        # Filtros de texto
        if query:
            condiciones = []

            if not filters_lower:
                # Sin filtros: solo por nombre
                condiciones.append("LOWER(c.nombre_contenido) LIKE ?")
                params.append(f"%{query.lower()}%")
            else:
                if "id" in filters_lower:
                    condiciones.append("CAST(c.id AS TEXT) LIKE ?")
                    params.append(f"%{query}%")
                if "author" in filters_lower:
                    condiciones.append("LOWER(c.autor) LIKE ?")
                    params.append(f"%{query.lower()}%")
                if "categorias" in filters_lower:
                    condiciones.append("LOWER(cat.nombre) LIKE ?")
                    params.append(f"%{query.lower()}%")
                if not any(f in ["id", "author", "categorias"] for f in filters_lower):
                    # Si los filtros no incluyen campos de texto específicos
                    condiciones.append("LOWER(c.nombre_contenido) LIKE ?")
                    params.append(f"%{query.lower()}%")

            if condiciones:
                sql += " AND (" + " OR ".join(condiciones) + ")"

        self.cursor.execute(sql, params)
        result = self.cursor.fetchall()

        lista = []
        for row in result:
            lista.append({
                "id": row[0],
                "title": row[1],
                "author": row[2],
                "type": row[3] + "/" + row[4] if admi else row[3],
                "category": row[5]
            })

        return lista


    # [IDF-0065] Retorna cierto contenido por su Id de la tabla contenidos.
    def getContent(self, content_id):
        """
        Obtiene los detalles completos de un contenido por su ID.

        Parámetros:
            content_id (int): ID del contenido.

        Retorna:
            dict: Información del contenido o None si no existe.
        """        
        query = '''
            SELECT 
                c.id, c.Archivo_bytes, c.nombre_contenido, c.autor, c.precio,
                c.extension, cat.ruta, c.rating, c.descripcion, c.tipo_contenido,
                c.downloaded, c.estado, c.id_promocion
            FROM contenidos c
            LEFT JOIN categorias cat ON c.categoria_id = cat.id
            WHERE c.id = ?
        '''
        self.cursor.execute(query, (content_id,))
        row = self.cursor.fetchone()

        if row:
            keys = ['id', 'src', 'title', 'author', 'price', 'extension', 'category',
                    'rating', 'description', 'type', 'downloaded', 'estado', 'id_promocion']
            content_dict = dict(zip(keys, row))

            # Convertir binario a data URL
            content_dict["src"] = C_Contenidos._generar_data_url(
                content_dict["src"], content_dict["type"], content_dict["extension"]
            )
            content_dict["rating"] = round(content_dict["rating"],2)

            # Verificar si tiene promoción
            id_prom = content_dict.get("id_promocion")
            if id_prom is not None:
                self.cursor.execute("SELECT titulo_de_descuento, descuento FROM promociones WHERE id = ?", (id_prom,))
                promo_row = self.cursor.fetchone()
                if promo_row:
                    titulo_prom, descuento = promo_row
                    precio_final = round(float(content_dict["price"]) * (1 - descuento), 2)
                    content_dict["price"] = f"<span style='color:green;'>{precio_final}</span> <s>{content_dict['price']}</s> ({titulo_prom})"

            return content_dict
        else:
            return None

    # [IDF-0066] Retorna el precio de cierto contenido por id.
    def obtenerPrecio(self, content_id):
        """
        Devuelve el precio de un contenido dado su ID.

        Parámetros:
            content_id (int)

        Retorna:
            float: Precio del contenido.
        """
        query = "SELECT precio FROM contenidos WHERE id = ?"
        self.cursor.execute(query, (content_id,))
        return self.cursor.fetchone()[0]

    # [IDF-0067] Veritica si un contenido contiene una promoción en la tabla promociones.
    def verificarPromocion(self, idC):
        """
        Indica si un contenido tiene una promoción asignada.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            bool: True si tiene promoción, False en caso contrario.
        """        
        self.cursor.execute("SELECT id_promocion FROM contenidos WHERE id = ?", (idC,))
        resultado = self.cursor.fetchone()
        
        # Retorna True si hay una promoción asociada, False si no
        return resultado is not None and resultado[0] is not None
    
    # [IDF-0068] Retorna el archivo binario de un contenido id para descargarlo.
    def obtenerBinarioPorID(self, idC):
        """
        Obtiene el archivo binario, nombre y extensión de un contenido.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            dict: Información del archivo, o None si no existe.
        """
        self.cursor.execute("SELECT Archivo_bytes, nombre_contenido, extension FROM contenidos WHERE id = ?", (idC,))
        row = self.cursor.fetchone()
        if row:
            return {
                "src": row[0],  # binario
                "title": "_".join(row[1].split(" ")),
                "extension": row[2]  # etc.
            }
        return None
    
    # [IDF-0149] Verificar si ya existe registro en la tabla descarga.
    def downloadedContentVerificate(self, id_contenido, id_usuario):
        """
        Verifica si el usuario ya descargó el contenido.

        Parámetros:
            id_contenido (int), id_usuario (int)

        Retorna:
            bool o resultado (tuple) si existe, None si no.
        """
        query_check_descarga = """
            SELECT downloaded FROM descarga
            WHERE id_usuario = ? AND id_contenido = ?
        """
        self.cursor.execute(query_check_descarga, (id_usuario, id_contenido))
        result = self.cursor.fetchone()
        return result

    # [IDF-0069] Actualiza el número de descargas hechas de cierto contenido.
    def downloadCount(self, id_contenido, id_usuario):
        """
        Incrementa el contador de descargas para un contenido y usuario.

        Parámetros:
            id_contenido (int), id_usuario (int)

        Retorna:
            None
        """
        # Aumentar contador global del contenido
        query_update_contenido = """
            UPDATE contenidos SET
                downloaded = downloaded + 1
            WHERE id = ?
        """
        self.cursor.execute(query_update_contenido, (id_contenido,))
        self.conn.commit()
        
        fecha_actual = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        if self.downloadedContentVerificate(id_contenido, id_usuario):
            # Ya existe, actualizar contador y fecha
            query_update_descarga = """
                UPDATE descarga SET
                    downloaded = downloaded + 1,
                    fecha = ?
                WHERE id_usuario = ? AND id_contenido = ?
            """
            self.cursor.execute(query_update_descarga, (fecha_actual, id_usuario, id_contenido))
        else:
            # No existe, insertar nuevo registro con fecha
            query_insert_descarga = """
                INSERT INTO descarga (id_usuario, id_contenido, downloaded, fecha)
                VALUES (?, ?, 1, ?)
            """
            self.cursor.execute(query_insert_descarga, (id_usuario, id_contenido, fecha_actual))

        self.conn.commit()
    
    # [IDF-0155] Se actualiza el estado de un contenido en la tabla contenidos.
    def actualizarEstadoContenido(self, idC):
        """
        Alterna el estado del contenido entre activo y desactivado.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            bool: True si fue desactivado, False si fue activado.
        """
        self.cursor.execute("SELECT estado FROM contenidos WHERE id = ?", (idC,))
        row = self.cursor.fetchone()
        
        if not row:
            print("Contenido no encontrado")
            return None

        estado_actual = row[0]
        
        if estado_actual.lower() == "activo":
            nuevo_estado = "desactivado"
            resultado = True
        else:
            nuevo_estado = "activo"
            resultado = False

        self.cursor.execute("UPDATE contenidos SET estado = ? WHERE id = ?", (nuevo_estado, idC))
        self.conn.commit()
        return resultado
    
    # [IDF-0163] Retorna de la tabla conteidos, el precio de un contenido y su id de promoción.
    def get_Prom(self, idC):
        """
        Devuelve el precio y la promoción asignada a un contenido.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            tuple: (precio, id_promocion) o None si no existe.
        """
        self.cursor.execute("SELECT precio, id_promocion FROM contenidos WHERE id = ?", (idC,))
        resultado = self.cursor.fetchone()
        if resultado is None:
            return None
        return resultado
    
    # [IDF-0171] La clase E_Contenidos le asignacion de una promocion a un contenido en su tabla contenidos.
    def asignarPromocion(self, idC, idP):
        """
        Asigna una promoción a un contenido.

        Parámetros:
            idC (int): ID del contenido.
            idP (int): ID de la promoción.

        Retorna:
            bool: True si la operación fue exitosa, False si falló.
        """
        try:
            self.cursor.execute("UPDATE contenidos SET id_promocion = ? WHERE id = ?", (idP, idC))
            self.conn.commit()
            return True
        except:
            return False
    
    # [IDF-0186] La clase E_Contenidos asigna una categoria una categoria a un contenido en la tabla contenidos. 
    def asignarCategoria(self, idC, idcat):
        """
        Asigna una categoría a un contenido específico.

        Parámetros:
            idC (int): ID del contenido.
            idcat (int): ID de la categoría.

        Retorna:
            bool: True si fue exitoso, False si ocurrió un error.
        """
        try:
            self.cursor.execute("UPDATE contenidos SET categoria_id = ? WHERE id = ?", (idcat, idC))
            self.conn.commit()
            return True
        except:
            return False
    
    # [IDF-0201] Consulta a las tablas contenidos,puntuaciones, descargas, para obtener las ultimas descargas de ciento cliente.
    def obtenerDescargasClienteTime(self, idU):
        """
        Retorna todas las descargas realizadas por un usuario específico, ordenadas por fecha (de más reciente a más antigua),
        incluyendo información del contenido y su puntuación promedio.
        """
        query = """
            SELECT 
                c.id,
                c.nombre_contenido,
                c.tipo_contenido,
                c.autor,
                d.fecha,
                IFNULL(AVG(p.puntuacion), 0) as puntuacion_promedio
            FROM descarga d
            JOIN contenidos c ON d.id_contenido = c.id
            LEFT JOIN puntuaciones p ON p.id_contenido = c.id
            WHERE d.id_usuario = ?
            GROUP BY c.id, d.fecha
            ORDER BY d.fecha DESC 
            LIMIT 10
        """
        self.cursor.execute(query, (idU,))
        resultados = self.cursor.fetchall()

        lista = []
        for fila in resultados:
            id_contenido, titulo, tipo, autor, fecha, puntuacion = fila
            lista.append({
                "id": id_contenido,
                "title": titulo,
                "type": tipo,
                "author": autor,
                "fecha_descarga": fecha,
                "rating": round(puntuacion, 2)
            })

        return lista


class C_Puntuacion:
    def __init__(self):
        pass
    
    # [IDF-0070] Pide a la tabla puntuaciones la puntuación de cierto contenido.
    def Obtener_Puntuacion(self, idU, idC):
        """
        Obtiene la puntuación que un usuario dio a un contenido.

        Parámetros:
            idU (int): ID del usuario.
            idC (int): ID del contenido.

        Retorna:
            int o None: Puntuación registrada o None si no existe.
        """        
        e_pun = E_Puntuaciones()
        return e_pun.Existe_Puntuacion(idU, idC)
    
    # [IDF-0071] Envia una puntuacion para cierto contenido a la tabla puntuaciones.
    def Enviar_Puntuacion(self, idU, idC, score):
        """
        Registra o actualiza la puntuación dada por un usuario a un contenido.

        Parámetros:
            idU (int): ID del usuario.
            idC (int): ID del contenido.
            score (int): Valor de puntuación (ej. 1 a 5).

        Retorna:
            None
            """
        e_pun = E_Puntuaciones()
        e_pun.Registrar_Puntuacion(idU,idC,score)

class E_Categorias:
    def __init__(self):
        self.conn = get_connection()
        self.cursor = self.conn.cursor()

    # [IDF-0188] La clase E_Categorias retorna todas las categorias disponibles.
    def obtener_categorias(self):
        """
        Retorna una lista con todas las categorías disponibles.

        Retorna:
            list: Lista de diccionarios con 'id' y 'category'.
        """
        self.cursor.execute("SELECT id, ruta FROM categorias")
        res = []
        for id, ruta in self.cursor.fetchall():
            res.append({'id':id,'category':ruta})
        return res
    
    # [IDF-0187] La clase E_Categorias genera y actualiza la ruta de una categoria segun su id.
    def gen_ruta(self, id):
        """
        Genera la ruta completa de una categoría basada en su jerarquía.

        Parámetros:
            id (int): ID de la categoría.

        Retorna:
            str: Ruta jerárquica (ej. 'Padre>Hijo>Nieto').
        """
        camino = []
        while id:
            self.cursor.execute("SELECT nombre, id_padre FROM categorias WHERE id = ?", (id,))
            fila = self.cursor.fetchone()
            if not fila:
                break
            nombre, id = fila
            camino.append(nombre)
        return ">".join(reversed(camino))
    
    # [IDF-0182] La clase E_Categorias añade a la tabla categorias la agregación de una categoria.
    def agregarCategoria(self, data):
        """
        Agrega una nueva categoría si no existe en la misma rama y actualiza su ruta jerárquica.

        Parámetros:
            data (dict): Contiene 'titulo' (nombre) y 'id_padre' (categoría superior).

        Retorna:
            dict/bool: {'success': True/False, 'message': ...} o False si hay error.
        """
        try:
            nombre = data.get("titulo")
            id_padre = data.get("id_padre")

            # Verificar si ya existe una categoría con el mismo nombre y padre
            self.cursor.execute("""
                SELECT id FROM categorias WHERE nombre = ? AND 
                (id_padre IS ? OR id_padre = ?)
            """, (nombre, id_padre, id_padre))

            if self.cursor.fetchone():
                return {'success':False, 'message':"Error: La categoria ya existe para esa rama."}

            # Insertar la nueva categoría
            query = "INSERT INTO categorias (nombre, id_padre) VALUES (?, ?)"
            self.cursor.execute(query, (nombre, id_padre))

            nuevo_id = self.cursor.lastrowid

            # Generar y guardar la ruta
            ruta = self.gen_ruta(nuevo_id)
            self.cursor.execute("UPDATE categorias SET ruta = ? WHERE id = ?", (ruta, nuevo_id))

            self.conn.commit()
            return {'success':True}

        except Exception as e:
            print(f"Error al agregar categoría: {e}")
            return False

class C_Categorias:
    def __init__(self):
        pass
    
    # [IDF-0189] El Controlador categorias enviar a su clase E_Categorias la petición de optener todas las categorias disponibles.
    def obtener_categorias(self):
        """
        Controlador: Obtiene todas las categorías disponibles desde E_Categorias.

        Retorna:
            list: Lista de categorías.
        """
        man_cat = E_Categorias()
        return man_cat.obtener_categorias()
    
    # [IDF-0181] El Controlador categorias enviar a su clase E_Categorias la agregación de una categoria.
    def agregarCategoria(self, data):
        """
        Controlador: Solicita agregar una nueva categoría a través de E_Categorias.

        Parámetros:
            data (dict): Contiene 'titulo' y 'id_padre'.

        Retorna:
            dict/bool: Resultado de la operación.
        """
        man_cat = E_Categorias()
        return man_cat.agregarCategoria(data)
    
class C_Promociones:
    def __init__(self):
        pass

    # [IDF-161] El Controlador contenidos solicita al controlador Promociones todas las promociones actuales.
    def obtenerPromociones(self):
        """
        Controlador: Obtiene todas las promociones registradas en E_Promociones.

        Retorna:
            list: Lista de promociones.
        """
        prom_manager = E_Promociones()
        return prom_manager.obtenerPromociones()
    
    # [IDF-0166] El controlador promociones solicita la promocion de cierto id promocion a la clase E_Promociones.
    def ObtenerPromocion(self, idprom):
        """
        Controlador: Recupera los datos de una promoción específica.

        Parámetros:
            idprom (int): ID de la promoción.

        Retorna:
            dict: Detalles de la promoción o None si no existe.
        """
        prom_manager = E_Promociones()
        return prom_manager.ObtenerPromocion(idprom)        
    
    # [IDF-0176] El Controlador promociones enviar a la clase  E_Promociones la agregación de una promocion.
    def agregarPromocion(self, data):
        """
        Controlador: Registra una nueva promoción a través de E_Promociones.

        Parámetros:
            data (dict): Contiene 'titulo', 'descuento' y 'dias'.

        Retorna:
            bool: True si se insertó correctamente, False si hubo error.
        """
        man_prom = E_Promociones()
        return man_prom.agregarPromocion(data)
    
    # [IDF-0202] El controlador promociones limpia las promociones pasadas de la clase E_Promociones.
    def limpiarPromocionesVencidas(self):
        """
        Controlador: Solicita eliminar las promociones de E_Promociones.
        Retorna: None
        """
        man_prom = E_Promociones()
        man_prom.limpiarPromocionesVencidas()

class C_Transacciones:
    def __init__(self):
        pass

    # [IDF-0072] Verifica si el tipo de tarjeta es valido.
    def verificarMetPago(self, Ncard, cardType):
        """
        Controlador: Verifica si el tipo de tarjeta ingresado está dentro de los bancos soportados.

        Parámetros:
            Ncard (str): Número de la tarjeta (no se valida aquí).
            cardType (str): Tipo de tarjeta ('mastercard', 'bcp', 'visa', 'paypal').

        Retorna:
            bool: True si el tipo es válido, False en caso contrario.
        """ 
        generate_Bancos_disponibles = lambda a: a in ["mastercard","bcp","visa","paypal"]
        return generate_Bancos_disponibles(cardType)
    
    # [IDF-0073] Realizar en envio a la tabla recargas de una solicitud de recargas y verificar si la tarjeta tiene el saldo suficiente.
    def realizarPago(self, user_id, amount, Ncard):
        """
        Controlador: Simula el pago con tarjeta y registra una solicitud de recarga.

        Parámetros:
            user_id (int): ID del usuario.
            amount (float): Monto a recargar.
            Ncard (str): Número de tarjeta.

        Retorna:
            int: 0 si el pago fue exitoso, 1 si falló.
        """        
        pagoTarjeta = lambda a,b : 1
        if pagoTarjeta(amount, Ncard):
            controller = E_Recargas()
            controller.registrarSolicitud(amount, user_id)
            return 0
        return 1
    
    # [IDF-0074] Pide a la tabla recargas la lista de peticiones de recargas.
    def obtenerListaPeticiones(self):
        """
        Controlador: Solicita a E_Recargas la lista de solicitudes de recarga pendientes.

        Retorna:
            list: Lista de recargas solicitadas.
        """
        controller = E_Recargas()
        return controller.obtenerListaPeticiones()
    
    # [IDF-0075] Pide a la clase de recargas el historial de descargas de cierto usuario.
    def obtenerRecargasCliente(self, idU):
        """
        Controlador: Solicita a E_Recargas el historial de recargas de un usuario.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            list: Lista de recargas realizadas por el usuario.
        """    
        controller = E_Recargas()
        return controller.obtenerRecargasCliente(idU)
    
    # [IDF-0076] Envia a la clase recargas la aprobación de cierta recarga.
    def aprobarRecarga(self, id_recarga):
        """
        Controlador: Aprueba una solicitud de recarga.

        Parámetros:
            id_recarga (int): ID de la recarga a aprobar.

        Retorna:
            tuple: (ID del usuario, monto aprobado).
        """        
        controller = E_Recargas()
        id_user, cantidad = controller.aprobarRecarga(id_recarga)
        return id_user, cantidad
    
    # [IDF-0077] Verifica en las respectivas clases de promociones y contenido para retornar el precio final de cierto contenido que tenga un descuento.
    def ProcesarPrecioFinal(self, idC):
        """
        Controlador: Calcula el precio final del contenido con descuento si aplica.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            float: Precio final (redondeado a 2 decimales).
        """        
        man_cont = C_Contenidos()
        precio_original, id_promocion = man_cont.get_Prom(idC)

        if id_promocion is None:
            return precio_original

        man_prom = C_Promociones()
        resultado_promo = man_prom.ObtenerPromocion(id_promocion)

        descuento = resultado_promo[0]  # Esperado entre 0.0 y 1.0
        precio_final = precio_original * (1 - descuento)
        return round(precio_final, 2)

    # [IDF-0078] Solicita a la clase usuarios la actualizacion del saldo de cierto cliente.
    def actualizarSaldo(self, idU, precio, recarga=False):
        """
        Controlador: Solicita a E_Usuarios actualizar el saldo del cliente.

        Parámetros:
            idU (int): ID del usuario.
            precio (float): Monto a descontar del saldo.
            recarga (bool): Determina si fue una recarga, caso contrario es retiro, regalo, o compra.
        """        
        controller = E_Usuarios()
        controller.actualizarSaldo(idU, precio, recarga)

    # [IDF-0079] Envia a las clases compras o regalos cierto contenido para ser registrado.
    def registrarCompra(self, idU, idC, precio, id_des=None):
        """
        Controlador: Registra la compra o regalo de un contenido.

        Parámetros:
            idU (int): ID del comprador.
            idC (int): ID del contenido.
            precio (float): Precio pagado.
            id_des (int, opcional): ID del destinatario si es regalo.
        """        
        if id_des==None:
            uscont = E_Compras()
            uscont.registrarCompra(idU,idC,precio,'compra')
        else:
            uscont = E_Regalos()
            uscont.registrarRegalo(idU,idC,precio,'regalo',id_des)

    # [IDF-0080] Verifica en las clases compras y regalos si cierto contenido le pertenece a cierto cliente.
    def verificarContenido(self, idu, idc):
        """
        Controlador: Verifica si el contenido le pertenece al usuario.

        Parámetros:
            idu (int): ID del usuario.
            idc (int): ID del contenido.

        Retorna:
            bool: True si ya posee el contenido, False en caso contrario.
        """        
        us_trans = E_Compras()
        us_rega = E_Regalos()     
        if us_trans.verificarContenido(idu,idc) or us_rega.verificarContenidoDestinatario(idu,idc):
            return True
        return False
    
class C_Contenidos:
    def __init__(self):
        pass

    # [IDF-0081] Envia a la clase de entidad contenidos el registro de un contenido.
    def registrarContenido(self, data):
        """
        Controlador: Registra un nuevo contenido multimedia.

        Parámetros:
            data (dict): Información del contenido (titulo, autor, tipo, etc.).
        """        
        contenidos = E_Contenidos()
        contenidos.registrarContenido(data)
    
    # [IDF-0082] Envia a la clase de entidad contenidos actulizar un cierto contenido.
    def actualizarContenido(self, data):
        """
        Controlador: Actualiza la información de un contenido existente.

        Parámetros:
            data (dict): Información actualizada del contenido.
        """        
        contenidos = E_Contenidos()
        contenidos.actualizarContenido(data)

    # [IDF-0083] Envia a la clase de entidad contenidos la consulta de cierto contenido por un query y filtros.
    def consultarDatos(self, query, filters):
        """
        Controlador: Consulta contenidos por título o autor, aplicando filtros por tipo.

        Parámetros:
            query (str): Texto a buscar (en título o autor).
            filters (list): Lista de tipos a filtrar (ej: ['imagen', 'audio', 'video', 'author']).

        Retorna:
            list: Lista de contenidos que coinciden con los filtros y el texto.
        """        
        query = query.lower().strip()
        resultados = []

        contenidos = E_Contenidos()
        A =  contenidos.obtenerContenidos()


        aut = 0 
        if 'author' in filters:
            filters.remove('author')
            aut = 1

        for item in A:
            if len(filters)==0 or item["type"] in filters:
                titulo = item.get('title', '').lower()
                author = item.get('author', '').lower()
                if query in titulo or (aut and query in author):
                    resultados.append({
                        'title': titulo,
                        'author': author,
                        'type': item["type"],
                        'id': item["id"]
                    })

        return resultados
    
    # [IDF-0084] Envia a la clase de entidad contenidos la busqueda de cierto contenido por query y filtros pero tambien por id, para la busqueda de informacion de un administrador.
    def solicitar_info_contenido(self, query, filters, admi=False):
        """
        Controlador: Solicita información de contenido específico a E_Contenidos.

        Parámetros:
            query (str): Texto de búsqueda.
            filters (list): Lista de filtros a aplicar.
            admi (bool): Indicador si la consulta es administrativa.

        Retorna:
            list: Resultados de la búsqueda de contenido.
        """        
        contenidos = E_Contenidos()
        return contenidos.Buscar_info(query,filters, admi)
    
    # [IDF-0085] Funcion que conbierte el contenido binario a Base64, leible para el frontend.
    @staticmethod
    def _generar_data_url(bin_data, tipo, extension):
        """
        Genera una URL de tipo data URI para contenido binario.

        Parámetros:
            bin_data (bytes): Datos binarios del contenido.
            tipo (str): Tipo de contenido ('imagen', 'audio', 'video').
            extension (str): Extensión del archivo.

        Retorna:
            str: Cadena codificada en base64 lista para uso en el frontend.
        """        
        # Parte	Explicación:
        # data:	Esquema que indica que se trata de un Data URL.
        # image/png	Tipo MIME del contenido (imagen en formato PNG en este caso).
        # ;base64	Indica que los datos están codificados en Base64.
        # iVBORw0KGgoAAA...	Los datos binarios del archivo codificados en Base64.
        # data_url = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAA..."
        # <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAA..." />        
        mime_types = {
            "imagen": f"image/{extension}",
            "audio": f"audio/{extension}",
            "video": f"video/{extension}"
        }
        mime_type = mime_types.get(tipo, "application/octet-stream")
        encoded = base64.b64encode(bin_data).decode("utf-8")
        return f"data:{mime_type};base64,{encoded}"
    
    # [IDF-0086] Envia a la clase de entidad contenidos obtener una lista de los top 10 más descargados de cada tipo de contenido.
    @staticmethod
    def getTopContent(parameter):
        """
        Controlador: Solicita los 10 contenidos más descargados de cada tipo.

        Retorna:
            list: Lista de contenidos top (imágenes, audios, videos).
        """        
        contenidos = E_Contenidos()
        todos = contenidos.obtenerContenidos(top=parameter)

        top_imagenes = []
        top_audios = []
        top_videos = []

        counter = {'imagen':[1,[]],'video':[1,[]],'audio':[1,[]]}

        for item in todos:
            tipo = item['type']
            if counter[tipo][0]<11:
                item['title'] =  str(counter[tipo][0]) + ". "+ item['title']
                counter[tipo][0] += 1
                counter[tipo][1].append(item)
                
            # if tipo == 'imagen' and len(top_imagenes) < 10:
            #     item['title'] =  str(len(top_imagenes)) + ". "+ item['title']
            #     top_imagenes.append(item)
            # elif tipo == 'audio' and len(top_audios) < 10:
            #     item['title'] =  str(len(top_audios)+1) + ". "+ item['title']
            #     top_audios.append(item)
            # elif tipo == 'video' and len(top_videos) < 10:
            #     item['title'] =  str(len(top_videos)+1) + ". "+ item['title']
            #     top_videos.append(item)

            # Si ya tienes 10 de cada tipo, puedes salir del bucle
            if counter["imagen"][0] == 11 and counter["video"][0]  == 11 and counter["audio"][0]  == 11:
                break

        return counter["imagen"][1] + counter["video"][1] + counter["audio"][1]
    
    # [IDF-0087] Envia a la clase de entidad contenidos obtener cierto contenido por su id.
    def getContent(self, content_id):
        """
        Controlador: Obtiene los datos de un contenido por su ID.

        Parámetros:
            content_id (int): ID del contenido.

        Retorna:
            dict: Información del contenido.
        """        
        contenidos = E_Contenidos()
        return contenidos.getContent(content_id)
    
    # [IDF-0088] Envia a la clase de entidad contenidos, obtener el precio de cierto contenido por su id.
    def obtenerPrecio(self, content_id):
        """
        Controlador: Solicita el precio de un contenido.

        Parámetros:
            content_id (int): ID del contenido.

        Retorna:
            float: Precio del contenido.
        """        
        contenidos = E_Contenidos()
        return contenidos.obtenerPrecio(content_id)
    
    # [IDF-0089] Envia a la clase de entidad contenidos, la verificación de promocion de cierto contenido.
    def verificarPromocion(self, idC):
        """
        Controlador: Verifica si un contenido tiene promoción.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            bool: True si tiene promoción, False si no.
        """        
        contenidos = E_Contenidos()
        return contenidos.verificarPromocion(idC)
    
    # [IDF-0090] Solicita a la clase controlador promociones  la puntuación a registrar pra cierto contenido.
    def Obtener_Puntuacion(self, idU, idC):
        """
        Controlador: Obtiene la puntuación de un contenido para un usuario.

        Parámetros:
            idU (int): ID del usuario.
            idC (int): ID del contenido.

        Retorna:
            float: Puntuación registrada.
        """        
        c_pun = C_Puntuacion()
        return c_pun.Obtener_Puntuacion(idU, idC)
    
    # [IDF-0091] Envia a la clase controlador promociones  la puntuación a registrar pra cierto contenido.
    def Enviar_Puntuacion(self, idU, idC, score):
        """
        Controlador: Registra una puntuación para un contenido.

        Parámetros:
            idU (int): ID del usuario.
            idC (int): ID del contenido.
            score (float): Puntuación otorgada.
        """
        ctr = C_Puntuacion()
        ctr.Enviar_Puntuacion(idU,idC,score)
    
    # [IDF-0092] Envia a la clase de entidad contenidos, la solicitud de obtener toda la información de cierto contenido para ser descargado
    def obtenerContenidoBinarios(self, idC, idU):
        """
        Controlador: Obtiene el binario del contenido y aumenta contador de descarga.

        Parámetros:
            idC (int): ID del contenido.
            idU (int): ID del usuario.

        Retorna:
            tuple: (tipo MIME, binarios, nombre del archivo)
        """        
        conte = E_Contenidos()
        contenido  = conte.obtenerBinarioPorID(idC)
        conte.downloadCount(idC, idU)

        if contenido:
            bin_data = contenido['src']
            extension = contenido['extension']
            filename = contenido['title'].replace(" ", "_") + "." + extension

            mime_map = {
                        # Imágenes
                        "png": "image/png",
                        "jpg": "image/jpeg",
                        "jpeg": "image/jpeg",
                        "gif": "image/gif",
                        "bmp": "image/bmp",
                        "webp": "image/webp",
                        "svg": "image/svg+xml",
                        "tiff": "image/tiff",
                        "ico": "image/x-icon",

                        # Audio
                        "mp3": "audio/mpeg",
                        "wav": "audio/wav",
                        "ogg": "audio/ogg",
                        "aac": "audio/aac",
                        "flac": "audio/flac",
                        "m4a": "audio/mp4",
                        "mid": "audio/midi",
                        "oga": "audio/ogg",

                        # Video
                        "mp4": "video/mp4",
                        "webm": "video/webm",
                        "mov": "video/quicktime",
                        "avi": "video/x-msvideo",
                        "mkv": "video/x-matroska",
                        "flv": "video/x-flv",
                        "wmv": "video/x-ms-wmv",
                        "3gp": "video/3gpp",
            }
            mime_type = mime_map.get(extension.lower(), "application/octet-stream") 
            return mime_type, bin_data, filename
        return None,None,None
    
    # [IDF-153] El controlador contenidos solicita a la tabla E_Contenidos, si cierto contenido fue descargado.
    def downloadedContentVerificate(self, idc, idu):
        """
        Controlador: Verifica si el usuario ya descargó un contenido.

        Parámetros:
            idc (int): ID del contenido.
            idu (int): ID del usuario.

        Retorna:
            bool: True si ya lo descargó, False si no.
        """        
        e_con = E_Contenidos()
        return e_con.downloadedContentVerificate(idc,idu) is not None
    
    # [IDF-154] El controlador contenidos solicita actualizar el estado de un contenido en su tabla de E_Contenidos.
    def actualizarEstadoContenido(self, idC):
        """
        Controlador: Actualiza el estado (activo/inactivo) del contenido.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            bool: Resultado de la operación.
        """        
        e_con = E_Contenidos()
        return e_con.actualizarEstadoContenido(idC)
    
    # [IDF-160] El Controlador contenidos solicita al controlador Promociones todas las promociones actuales.
    def obtenerPromociones(self):
        """
        Controlador: Solicita la lista de promociones actuales.

        Retorna:
            list: Lista de promociones activas.
        """        
        prom_manager = C_Promociones()
        return prom_manager.obtenerPromociones()   

    # [IDF-0164] el controlador conteidos solicita a la clase E_Contenidos cierto promocion por su id.
    def get_Prom(self, idC):
        """
        Controlador: Obtiene promoción asociada a un contenido.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            tuple: (precio original, ID promoción) o None.
        """        
        e_con = E_Contenidos()
        return e_con.get_Prom(idC)
    
    # [IDF-0170] El controlador contenidos envia la clase E_Contenidos la asignacion de una promocion.
    def asignarPromocion(self, idC, idP):
        """
        Controlador: Asigna promoción a un contenido.

        Parámetros:
            idC (int): ID del contenido.
            idP (int): ID de la promoción.

        Retorna:
            bool: True si se asignó correctamente.
        """        
        e_con = E_Contenidos()
        return e_con.asignarPromocion(idC,idP)
    
    # [IDF-0175] El Controlador contenidos enviar a su controlador promociones la agregación de una promocion.
    def agregarPromocion(self, data):
        """
        Controlador: Registra nueva promoción.

        Parámetros:
            data (dict): Información de la promoción.

        Retorna:
            bool: True si se agregó exitosamente.
        """        
        man_prom = C_Promociones()
        return man_prom.agregarPromocion(data)   
        
    # [IDF-0180] El Controlador contenidos enviar a su controlador categorias la agregación de una categoria.
    def agregarCategoria(self, data):
        """
        Controlador: Registra nueva categoría.

        Parámetros:
            data (dict): Información de la categoría.

        Retorna:
            bool: True si se agregó exitosamente.
        """        
        man_cat = C_Categorias()
        return man_cat.agregarCategoria(data)

    # [IDF-0185] El Controlador contenidos enviar a su clase  E_Contenidos la asignacion de una categoria.
    def asignarCategoria(self, idC,idcat):
        """
        Controlador: Asigna categoría a un contenido.

        Parámetros:
            idC (int): ID del contenido.
            idcat (int): ID de la categoría.

        Retorna:
            bool: True si se asignó correctamente.
        """        
        man_content = E_Contenidos()
        return man_content.asignarCategoria(idC,idcat)

    # [IDF-0192] El Controlador contenidos enviar a su controlador categorias la obtencion de todas las categorías.
    def obtener_categorias(self):
        """
        Controlador: Solicita todas las categorías registradas.

        Retorna:
            list: Lista de categorías.
        """        
        man_cat = C_Categorias()
        return man_cat.obtener_categorias()
    
    # [IDF-0203] El controlador Contenidos envia a su controlador promociones la accion de limpiar las promociones pasadas.
    def limpiarPromocionesVencidas(self):
        """
        Controlador: Envia el mensaje a su controlador promociones de limpiar las promociones vencidas.
        Retorna: None
        """   
        man_prom = C_Promociones()
        man_prom.limpiarPromocionesVencidas()

class C_Usuario:
    def __init__(self):
        """
        Constructor de la clase C_Usuario.
        Inicializa el ID del usuario como None.
        """        
        self.id = None
    
    # [IDF-0093] Solicita a la clase de entidad usuarios, obtener los datos de cierto usuario por su id.
    def getDataUser(self, id_user):
        """
        Controlador: Solicita los datos del usuario según su ID.

        Parámetros:
            id_user (int): ID del usuario.

        Retorna:
            dict: Datos del usuario.
        """        
        usuarios = E_Usuarios()
        return usuarios.obtenerUser(id_user)
    
    # [IDF-0094] Solicita a la clase controlador contenidos, buscar contenidos con cierto query y filtro.
    def Buscar(self, query,filters):
        """
        Controlador: Realiza la búsqueda de contenidos usando un query y filtros.

        Parámetros:
            query (str): Término de búsqueda.
            filters (dict): Filtros aplicables a la búsqueda.

        Retorna:
            list: Lista de contenidos que coinciden.
        """        
        # content_manager = C_Contenidos()
        # return content_manager.consultarDatos(query,filters)
        content_manager = C_Contenidos()
        resultados = content_manager.solicitar_info_contenido(query, filters)
        return resultados
    
    # [IDF-0095] Solicita a la controlador contenidos, obtener cierto contenido por su id.
    def seleccionarContent(self, content_id):
        """
        Controlador: Solicita un contenido por su ID.

        Parámetros:
            content_id (int): ID del contenido.

        Retorna:
            dict: Datos del contenido.
        """        
        content_manager = C_Contenidos()
        return content_manager.getContent(content_id)
    
    # [IDF-0096] Solicita a la clase de entidad usuarios, verificar los datos de cierto usuario por su id.
    def loginVerificar(self, username, password):
        """
        Controlador: Verifica las credenciales de inicio de sesión del usuario.

        Parámetros:
            username (str): Nombre de usuario.
            password (str): Contraseña.

        Retorna:
            dict or None: Datos del usuario si son válidos; None si no lo son.
        """        
        usuarios = E_Usuarios()
        return usuarios.verificarLogin(username,password)
    
    # [IDF-0097] Solicita a la clase controlador Contenidos, los top contenidos a mostrar.
    def getContentView(self):
        """
        Controlador: Solicita los contenidos más populares (Top 10 por tipo).

        Retorna:
            list: Lista combinada de los top contenidos.
        """        
        content_manager = C_Contenidos()
        return content_manager.getTopContent()

    # [IDF-0098] Solicita a la clase de entidad usuarios, verificar si cierto username esta en uso.
    def validarRegistro(self, user):
        """
        Controlador: Verifica si el nombre de usuario ya está registrado.

        Parámetros:
            user (str): Nombre de usuario.

        Retorna:
            bool: True si ya existe, False si está disponible.
        """        
        us = E_Usuarios()
        return us.validarDatos(user)

    # [IDF-0099] Envia a la clase de entidad usuarios, el registro de un nuevo usuario.
    def registrarUsuario(self, data):
        """
        Controlador: Registra un nuevo usuario en el sistema.

        Parámetros:
            un diccionario que contiene:
                username (str): Nombre de usuario.
                password (str): Contraseña.
                email (str): Correo electrónico.
                nombre (str): nombre del usuario.
                apellido_paterno (str): apelledio paterno del usuario.
                apellido_materno (str): apelledio materno del usuario.
        Retorna:
            None
        """        
        us = E_Usuarios()
        id = us.registrarUsuario(data)
        man_not = E_Notificaciones()
        msg = f"Si llegas a consumir más de {NECESARIO} creditos, obtienes un descuento del {ACUMULADO_DESCUENTO*100}% en cualquier contenido."
        man_not.registrarNotificacionRecarga(id, msg)


    # [IDF-0100] Solicita a la clases controlador transacciones y contenidos, verificar si fue comprado por cierto usuario y si fue puntuado.
    def verificarContenido(self, idU, idC):
        """
        Controlador: Verifica si el usuario compró y puntuó el contenido.

        Parámetros:
            idU (int): ID del usuario.
            idC (int): ID del contenido.

        Retorna:
            dict: Diccionario con claves 'success' y 'hasRated'.
        """        
        c_usContenido = C_Transacciones()
        content_manager = C_Contenidos()
        res = {'success':c_usContenido.verificarContenido(idU, idC), 
               'hasRated':content_manager.downloadedContentVerificate(idC, idU)}
        return res
    
    # [IDF-00101] Solicita a la clase de entidad compras, la lista de compras realizadas por cierto cliente.
    def obtenerDescargasCliente(self, idU):
        """
        Controlador: Solicita la lista de contenidos descargados por un usuario.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            list: Lista de contenidos descargados.
        """        
        uscont = E_Compras()
        return uscont.obtenerDescargasCliente(idU)

    # [IDF-00101] Solicita a la clase de entidad compras, la lista de compras realizadas por cierto cliente.
    def obtenerDescargasClienteTime(self, idU):
        """
        Controlador: Solicita la lista de contenidos descargados por un usuario.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            list: Lista de contenidos descargados.
        """        
        uscont = E_Contenidos()
        return uscont.obtenerDescargasClienteTime(idU)
        
    # [IDF-00102] Solicita a la clase controlador transacciones, la lista de recargas de cierto cliente.
    def obtenerRecargasCliente(self, idU):
        """
        Controlador: Solicita la lista de recargas realizadas por un usuario.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            list: Lista de recargas.
        """        
        controller_trans = C_Transacciones()
        return controller_trans.obtenerRecargasCliente(idU)
    
    # [IDF-00103] Solicita a la clase controlador contenidos, cierto contenido, para ser descargado.
    def obtenerContenidoDescarga(self,content_id,idU):
        """
        Controlador: Solicita el contenido en formato binario para su descarga.

        Parámetros:
            content_id (int): ID del contenido.
            idU (int): ID del usuario.

        Retorna:
            tuple: (mime_type, bin_data, filename) si existe, caso contrario (None, None, None).
        """        
        controller = C_Contenidos()
        return controller.obtenerContenidoBinarios(content_id, idU) 

class C_Cliente(C_Usuario):
    def __init__(self):
        """
        Constructor de la clase C_Cliente.
        Hereda de la clase C_Usuario.
        """        
        super().__init__()

    # [IDF-00104] Solicita a la clase controlador transacciones, el metodo de pago de tarjeta  y si tiene saldo suficiente en la tarjeta.
    def enviarSolicitud(self, Ncard, amount, cardType, id_user):
        """
        Controlador: Verifica el método de pago y realiza el pago si hay saldo suficiente.

        Parámetros:
            Ncard (str): Número de tarjeta.
            amount (float): Monto a pagar.
            cardType (str): Tipo de tarjeta.
            id_user (int): ID del usuario.

        Retorna:
            dict: Resultado de la operación.
        """        
        controller = C_Transacciones()
        if not controller.verificarMetPago(Ncard,cardType):
            return {"success": False, "message":"Metodo de pago invalido"}
        if controller.realizarPago(id_user, amount, Ncard):
            return {"success": False, "message":"Saldo insuficiente"}
        return {"success": True}
    
    # [IDF-00105] Solicita a la clase entidad usuarios, el saldo actual del cliente.
    def obtenerSaldo(self, id_user):
        """
        Controlador: Solicita el saldo actual del usuario.

        Parámetros:
            id_user (int): ID del usuario.

        Retorna:
            float: Saldo actual.
        """        
        usuarios = E_Usuarios()
        return usuarios.obtenerSaldo(id_user)
    
    # [IDF-0204] El controlador cliente solicita a su clase E_Usuarios la accion de verificar el acumulado de cierto cliente.
    def verificarAcumulado(self, idU):
        """
        Contrador: Verifica para cierto cliente su acumulado, para ver si le permite el descuento por creditos consumidos.

        Retorna: (bool) True si cumple, caso contrario False.
        """
        usuarios = E_Usuarios()
        return usuarios.verificarAcumulado(idU)
    
    # [IDF-0205] El controlador cliente solicita a su clase E_Usuarios la accion de reiniciar el acumulado de cierto cliente.
    def consumirAcumulado(self, idU):
        """
        Contrador: Solicita para cierto cliente reiniciar su acumulado.

        Retorna: None
        """        
        usuarios = E_Usuarios()
        return usuarios.consumirAcumulado(idU)
    
    # [IDF-00106] Solicita a la clase controlador transacciones y contenidos, el pago de cierto contenido.
    def pagarContenido(self, idU, idC, id_des=None):
        """
        Controlador: Procesa el pago de un contenido, con o sin promoción.

        Parámetros:
            idU (int): ID del usuario que paga.
            idC (int): ID del contenido.
            id_des (int, opcional): ID del destinatario si es un regalo.

        Retorna:
            bool: True si se realizó el pago correctamente, False si no.
        """        
        controller_trans = C_Transacciones()
        controller_cont = C_Contenidos()
        saldo = self.obtenerSaldo(idU) 
        acumulado = self.verificarAcumulado(idU)

        if controller_cont.verificarPromocion(idC):
            precioFinal = controller_trans.ProcesarPrecioFinal(idC)
        else:
            precioFinal = controller_cont.obtenerPrecio(idC)
            if acumulado:
                precioFinal = precioFinal * (1 - ACUMULADO_DESCUENTO)
            print(precioFinal)

        if saldo > precioFinal:
            if acumulado:
                self.consumirAcumulado(idU)
            controller_trans.actualizarSaldo(idU, -precioFinal)
            if self.verificarAcumulado(idU):
                msg = f"Tiene un {ACUMULADO_DESCUENTO*100}% en tu siguiente compra."
                E_Notificaciones().registrarNotificacionRecarga(idU, msg)
                
        else:
            return False
        
        if id_des == None:
            controller_trans.registrarCompra(idU, idC, precioFinal)
        else:
            controller_trans.registrarCompra(idU, idC, precioFinal, id_des=id_des)
        return True
    
    # [IDF-00107] Solicita a la clase controlador contenidos, obtener la puntuación de cierto conteido.
    def Obtener_Puntuacion(self, idU):
        """
        Controlador: Solicita la puntuación dada por el usuario a un contenido.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            float: Puntuación del contenido.
        """        
        ctr = C_Contenidos()
        return ctr.Obtener_Puntuacion(idU)
    
    # [IDF-00108] Envia a la clase controlador contenidos, la puntuación de cierto conteido.
    def Enviar_Puntuacion(self, idU, idC, score):
        """
        Controlador: Envía la puntuación de un contenido.

        Parámetros:
            idU (int): ID del usuario.
            idC (int): ID del contenido.
            score (float): Puntuación.
        """       
        ctr = C_Contenidos()
        ctr.Enviar_Puntuacion(idU,idC,score)

    # [IDF-0109] Envia a la clase entiedad usuarios, controlador transacciones, y a entidad notificaciones, la compra de un regalo de cierto cliente a otro.
    def Enviar_destinatario(self, idU, idC, destinatario):
        """
        Controlador: Envía un contenido como regalo a otro usuario.

        Parámetros:
            idU (int): ID del remitente.
            idC (int): ID del contenido.
            destinatario (str): Nombre del usuario destinatario.

        Retorna:
            dict: Resultado de la operación.
        """        
        res = {'success':False}
        e_us = E_Usuarios()
        id_des = e_us.UsuarioExiste(destinatario, idU)
        if id_des==-1:
            res['msg']="El destinatario no existe."
        else:
            c_rega = C_Transacciones()
            if c_rega.verificarContenido(id_des, idC):
                res['msg'] = 'El destinatario ya tiene el contenido.'
            else:
                if not self.pagarContenido(idU, idC,id_des=id_des):
                    res['msg'] = 'Saldo insuficiente.'
                else:
                    res['success'] = True
                    e_notifi = E_Notificaciones()
                    from_user = e_us.obtenerUser(idU)['username']
                    e_notifi.registrarNotificacionRegalo(id_des, idC, f"Regalo de parte de {from_user}.")
        return res
    
    # [IDF-0110] Solicita a la clase entidad usuarios, validar el saldo de cierto cliente para ver si es posible registrarlo como ex-cliente.
    def SolicitarValidarSaldo(self, idU):
        """
        Controlador: Verifica si el saldo del usuario es cero para registrarlo como ex-cliente.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            bool: True si fue registrado como ex-cliente, False si no.
        """        
        if (self.obtenerSaldo(idU) == 0):
            usuarios = E_Usuarios()
            usuarios.registrar_Excliente(idU)
            return True
        return False
    
    # [IDF-0111] Solicita a la clase entidad notificaciones, obtener la lista de notifaciones para cierto usuario.
    def obtenerNotificaciones(self, idU):
        """
        Controlador: Solicita la lista de notificaciones del usuario.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            list: Lista de notificaciones.
        """        
        e_notifi = E_Notificaciones()
        res = e_notifi.obtenerListaNotificaciones(idU)
        #res.extend(e_notifi.obtenerListaNotificacionesRecargas(idU))
        return res
    
    # [IDF-0112] Envia a la clase entidad notificaciones, aceptar cierta notifacion para marcar como leida.
    def aceptarNotificacion(self, idN):
        """
        Controlador: Marca una notificación como leída.

        Parámetros:
            idN (int): ID de la notificación.

        Retorna:
            None
        """        
        e_notifi = E_Notificaciones()
        e_notifi.aceptarNotificacion(idN)
    
    # [IDF-0113] Solicita a la clase entidad usuarios y controlador transacciones, retirar el saldo de cierto usuario.
    def Retirar_Saldo(self, card, cardType, idU):
        """
        Controlador: Retira el saldo total del usuario hacia una tarjeta.

        Parámetros:
            card (str): Número de tarjeta.
            cardType (str): Tipo de tarjeta.
            idU (int): ID del usuario.

        Retorna:
            bool: True si se retiró el saldo correctamente.
        """        
        usuarios = E_Usuarios()
        monto = usuarios.obtenerSaldo(idU)
        #controller_trans = C_Transacciones()
        #controller_trans.RegistrarRetiro(card, cardType, idU, monto)
        usuarios.actualizarSaldo(idU, -monto)
        return True
    
class C_Administrador(C_Usuario):
    def __init__(self):
        super().__init__()
    
    # [IDF-0114] Solicita a la clase controlador transacciones, obtener la lista de recargas.
    def getRecargas(self):
        """
        Administrador: Solicita todas las peticiones de recarga pendientes.

        Retorna:
            list: Lista de solicitudes de recarga.
        """    
        controller = C_Transacciones()
        return controller.obtenerListaPeticiones()
    
    # [IDF-0115] Envia a la clase controlador transacciones y entidad notifaciones, la aprobación de cierta recarga.
    def aprobarRecarga(self, id_recarga):
        """
        Administrador: Aprueba una recarga y actualiza el saldo del usuario correspondiente.

        Parámetros:
            id_recarga (int): ID de la solicitud de recarga.
        """        
        controller = C_Transacciones()
        id_user,cantidad = controller.aprobarRecarga(id_recarga)
        controller.actualizarSaldo(id_user,cantidad, recarga=True)
        print(id_user,cantidad)
        # usuarios = E_Usuarios()
        # usuarios.actualizarSaldo(id_user, cantidad)
        e_noti = E_Notificaciones()
        e_noti.registrarNotificacionRecarga(id_user,f"Recarga de ${cantidad} aprobada.")

    # [IDF-0116] Envia a la clase controlador contenidos, un contenido nuevo a registrar.
    def ingresarAgregarContenido(self, datos):
        """
        Administrador: Envía los datos necesarios para registrar un nuevo contenido.

        Parámetros:
            datos (dict): Información del nuevo contenido.
        """        
        content_manager = C_Contenidos()
        content_manager.registrarContenido(datos)

    # [IDF-0117] Envia a la clase controlador contenidos, actualizar un contenido existente.
    def actualizarContenido(self, datos):
        """
        Administrador: Envía la actualización de datos para un contenido existente.

        Parámetros:
            datos (dict): Datos actualizados del contenido.
        """        
        content_manager = C_Contenidos()
        content_manager.actualizarContenido(datos)

    # [IDF-0118] Solicita a la clase controlador contenidos y entidad usuarios, buscar información sobre estos.
    def buscar_info(self, data):
        """
        Administrador: Realiza una búsqueda combinada en contenidos y usuarios según filtros.

        Parámetros:
            data (dict): Contiene 'query' (str) y 'filters' (list) para la búsqueda.

        Retorna:
            list: Resultados combinados de la búsqueda.
        """        
        resultados = []
        filters = data['filters']
        if 'cliente' not in filters:
            content_manager = C_Contenidos()
            resultados = content_manager.solicitar_info_contenido(data['query'], filters, True)
        if not ('audio' in filters or 'video' in filters or 'imagen' in filters or 'author' in filters):
            usuarios = E_Usuarios()
            resultados += usuarios.buscar_info_usuarios(data['query'])

        return resultados
    
    # [IDF-0119] Solicita a la clase entidad usuarios, obtener datos de cierto usuario según su id.
    def seleccionarUser(self, id):
        """
        Administrador: Solicita los datos de un usuario según su ID.

        Parámetros:
            id (int): ID del usuario.

        Retorna:
            dict: Información del usuario.
        """        
        usuarios = E_Usuarios()
        return usuarios.obtenerUser(id)

    # [IDF-0152] Envia al controlador Contenidos la actualización de estado de un contenido.
    def actualizarEstadoContenido(self, idC):
        """
        Administrador: Cambia el estado de un contenido (activo/inactivo).

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            dict: Estado actualizado del contenido.
        """        
        content_manager = C_Contenidos()
        return {"success": True, "estado": content_manager.actualizarEstadoContenido(idC)}
    
    # [IDF-159] El Controlador Administrador solicita al controlador Contenidos todas las promociones actuales.
    def obtenerPromociones(self):
        """
        Administrador: Solicita todas las promociones registradas.

        Retorna:
            list: Lista de promociones.
        """        
        content_manager = C_Contenidos()
        return content_manager.obtenerPromociones()

    # [IDF-0169] El controlador administrador envia al controlador contenidos la asignacion de una promocion.
    def asignarPromocion(self, idC, idP):
        """
        Administrador: Asigna una promoción a un contenido específico.

        Parámetros:
            idC (int): ID del contenido.
            idP (int): ID de la promoción.

        Retorna:
            bool: True si la asignación fue exitosa.
        """        
        man_content = C_Contenidos()
        return man_content.asignarPromocion(idC,idP)    
    
    # [IDF-0174] El Controlador administrador enviar a su controlador contenidos la agregación de una promocion.
    def agregarPromocion(self, data):
        """
        Administrador: Agrega una nueva promoción con los datos proporcionados.

        Parámetros:
            data (dict): Información de la nueva promoción.

        Retorna:
            bool: True si la promoción fue agregada exitosamente.
        """        
        man_content = C_Contenidos()
        return man_content.agregarPromocion(data)   
    
    # [IDF-0179] El Controlador administrador enviar a su controlador contenidos la agregación de una categoria.
    def agregarCategoria(self, data):
        """
        Administrador: Agrega una nueva categoría al sistema.

        Parámetros:
            data (dict): Información de la categoría a agregar.

        Retorna:
            bool: True si la categoría fue agregada exitosamente.
        """        
        man_content = C_Contenidos()
        return man_content.agregarCategoria(data)
    
    # [IDF-0184] El Controlador administrador enviar a su controlador contenidos la asignacion de una categoria.
    def asignarCategoria(self, idC,idcat):
        """
        Administrador: Asigna una categoría a un contenido.

        Parámetros:
            idC (int): ID del contenido.
            idcat (int): ID de la categoría.

        Retorna:
            bool: True si la asignación fue exitosa.
        """        
        man_content = C_Contenidos()
        return man_content.asignarCategoria(idC,idcat)

    # [IDF-0191] El Controlador administrador enviar a su controlador contenidos la obtencion de todas las categorías.
    def obtener_categorias(self):
        """
        Administrador: Solicita todas las categorías registradas.

        Retorna:
            list: Lista de categorías.
        """        
        man_content = C_Contenidos()
        return man_content.obtener_categorias()
    
    # [IDF-0206] Controlador administrador solcita obtener el ranking de descargas de clientes a la clase E_Usuarios.
    def obtenerRankingUsuariosPorDescargas(self):
        """
        Administrador: Solicita obtener el ranking de descargas de clientes a la clase E_Usuarios.

        Retorna:
            list: Lista de clientes ordenado por el número de descargas.
        """           
        man_us = E_Usuarios()
        return man_us.obtenerRankingUsuariosPorDescargas()
    
    # [IDF-0214] El Controlador Administrador solicita la función de obtener la tabla de cierto tipo.
    def getTable(self, tipo):
        """
        Administrador: Solicita obtener cierta tabla de un tipo, pueden ser de usuarios, recargas, etc, según el tipo.

        Retorna:
            list: Lista de resultados de cierta tabla.
        """           
        return getTable(tipo)
        
class Usuario:
    def __init__(self, user=None, id=None, ctr=C_Usuario()):
        """
        Inicializa un objeto Usuario.

        Parámetros:
            user (str): Nombre de usuario.
            id (int): ID del usuario.
            ctr (C_Usuario): Controlador asociado al usuario.
        """ 
        self.user = user
        self.id = id
        self.controller = ctr

    # [IDF-0120] Envía credenciales a su controlador Usuario: Iniciar sesión del usuario
    def iniciar_sesion(self, username, password):
        """
        Usuario: Inicia sesión con nombre de usuario y contraseña.

        Parámetros:
            username (str): Nombre de usuario.
            password (str): Contraseña del usuario.

        Retorna:
            bool: True si la autenticación es exitosa.
        """   
        auth, self.id = self.controller.loginVerificar(username, password)
        return auth

    # [IDF-0121] Solicita datos a su controlador Usuario: Buscar contenidos con filtros
    def Buscar(self, query, filters):
        """
        Usuario: Busca contenidos con filtros específicos.

        Parámetros:
            query (str): Término de búsqueda.
            filters (dict): Filtros aplicados.

        Retorna:
            list: Lista de contenidos encontrados.
        """ 
        print(self.user, self.id)
        return self.controller.Buscar(query, filters)

    # [IDF-0122] Envía solicitud a su controlador Usuario: Seleccionar un contenido específico
    def seleccionar(self, content_id):
        """
        Usuario: Selecciona un contenido específico.
        Parámetros:
            content_id (int): ID del contenido.

        Retorna:
            dict: Información del contenido seleccionado.
        """        
        return self.controller.seleccionarContent(content_id)

    # [IDF-0123] Solicita información a su controlador Usuario: Obtener los datos del usuario
    def getDataUser(self):
        """
        Usuario: Obtiene los datos del usuario.

        Retorna:
            dict: Información del usuario.
        """        
        return self.controller.getDataUser(self.id)

    # [IDF-0124] Envía datos a su controlador Usuario: Registrar un nuevo usuario (placeholder)
    def registrarU(self, data):
        """
        Usuario: Placeholder para registrar un nuevo usuario.

        Parámetros:
            data (dict): Datos del nuevo usuario.

        Retorna:
            int: Código de éxito o error.
        """
        return 1

    # [IDF-0125] Solicita información a su controlador Usuario: Obtener contenidos destacados o generales
    def getContentView(self):
        """
        Usuario: Obtiene los contenidos destacados o generales.

        Retorna:
            list: Lista de contenidos.
        """        
        return self.controller.getContentView()

    # [IDF-0126] Envía datos a su controlador Usuario: Validar y registrar un nuevo usuario
    def validarRegistro(self, data):
        """
        Usuario: Valida y registra un nuevo usuario.

        Parámetros:
            Un diccionario que contiene:
                username (str): Nombre de usuario.
                password (str): Contraseña.
                email (str): Correo electrónico.
                nombre (str): nombre del usuario.
                apellido_paterno (str): apelledio paterno del usuario.
                apellido_materno (str): apelledio materno del usuario.

        Retorna:
            int: 1 si el registro es exitoso, 0 si el usuario ya existe.
        """        
        if not self.controller.validarRegistro(data.get("username")):
            return 0
        self.controller.registrarUsuario(data)
        return 1

    # [IDF-0127] Verifica existencia a su controlador Usuario: Verificar si el usuario ya tiene un contenido
    def verificarContenido(self, idC):
        """
        Usuario: Verifica si ya posee cierto contenido.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            bool: True si el contenido ya fue adquirido.
        """        
        return self.controller.verificarContenido(self.id, idC)

    # [IDF-0128] Envía respuesta a su controlador Usuario: Aceptar una notificación
    def aceptarNotificacion(self, idN):
        """
        Usuario: Acepta una notificación.

        Parámetros:
            idN (int): ID de la notificación.
        """        
        self.controller.aceptarNotificacion(idN)

    # [IDF-0129] Solicita contenido a su controlador Usuario: Obtener contenido para descargar
    def obtenerContenidoDescarga(self, content_id):
        """
        Usuario: Solicita el contenido para descarga.

        Parámetros:
            content_id (int): ID del contenido.

        Retorna:
            dict: Información para la descarga.
        """        
        return self.controller.obtenerContenidoDescarga(content_id, self.id)


class Cliente(Usuario):
    def __init__(self, username, id):
        """
        Inicializa un objeto Cliente.

        Parámetros:
            username (str): Nombre de usuario.
            id (int): ID del cliente.
        """        
        super().__init__(user=username, id=id, ctr=C_Cliente())
        self.saldo = None
        self.estado_cuenta = None

    # [IDF-0130] Envía solicitud a su controlador Cliente: Solicitar recarga de saldo con tarjeta
    def ingresarMontoSolicitar(self, Ncard, amount, cardType):
        """
        Cliente: Solicita una recarga con tarjeta.

        Parámetros:
            Ncard (str): Número de tarjeta.
            amount (float): Monto a recargar.
            cardType (str): Tipo de tarjeta.

        Retorna:
            bool: True si la solicitud fue enviada correctamente.
        """        
        return self.controller.enviarSolicitud(Ncard, amount, cardType, self.id)

    # [IDF-0131] Solicita información a su controlador Cliente: Obtener el saldo actual del cliente
    def getSaldo(self):
        """
        Cliente: Obtiene el saldo actual.

        Retorna:
            float: Saldo del cliente.
        """        
        return self.controller.obtenerSaldo(self.id)

    # [IDF-0132] Envía pago a su controlador Cliente: Realizar el pago de un contenido
    def pagarContenido(self, idC):
        """
        Cliente: Realiza el pago por un contenido.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            bool: True si el pago fue exitoso.
        """        
        return self.controller.pagarContenido(self.id, idC)

    # [IDF-0133] Solicita información a su controlador Cliente: Obtener descargas realizadas por el cliente
    def obtenerDescargasCliente(self):
        """
        Cliente: Obtiene la lista de descargas realizadas.

        Retorna:
            list: Lista de descargas.
        """        
        return self.controller.obtenerDescargasCliente(self.id)

    # [IDF-0134] Solicita puntuación a su controlador Cliente: Obtener puntuación de contenido dada por un cliente
    def Obtener_Puntuacion(self, idU):
        """
        Cliente: Obtiene la puntuación que dio a un contenido.

        Parámetros:
            idU (int): ID del usuario.

        Retorna:
            int: Puntuación del contenido.
        """        
        return self.controller.Obtener_Puntuacion(idU)

    # [IDF-0135] Envía puntuación a su controlador Cliente: Enviar una puntuación para un contenido
    def Enviar_Puntuacion(self, idC, score):
        """
        Cliente: Envía una puntuación para un contenido.

        Parámetros:
            idC (int): ID del contenido.
            score (int): Puntuación del 1 al 5.

        Retorna:
            bool: True si la puntuación fue enviada correctamente.
        """        
        try:
            self.controller.Enviar_Puntuacion(self.id, idC, score)
        except Exception as e:
            print(f"Error al enviar puntuación: {e}")
            return False
        return True

    # [IDF-0136] Envía regalo a su controlador Cliente: Enviar un contenido a otro usuario como regalo
    def Enviar_destinatario(self, idC, destinatario):
        """
        Cliente: Envía un contenido como regalo a otro usuario.

        Parámetros:
            idC (int): ID del contenido.
            destinatario (str): Usuario destinatario.

        Retorna:
            bool: True si el regalo fue enviado correctamente.
        """        
        return self.controller.Enviar_destinatario(self.id, idC, destinatario)

    # [IDF-0137] Solicita notificaciones a su controlador Cliente: Obtener notificaciones del cliente
    def obtenerNotificaciones(self):
        """
        Cliente: Obtiene notificaciones pendientes.

        Retorna:
            list: Lista de notificaciones.
        """        
        return self.controller.obtenerNotificaciones(self.id)

    # [IDF-0138] Solicita validación a su controlador Cliente: Solicitar validación del saldo disponible
    def SolicitarValidarSaldo(self):
        """
        Cliente: Solicita validación de saldo disponible.

        Retorna:
            bool: True si hay saldo suficiente.
        """        
        return self.controller.SolicitarValidarSaldo(self.id)

    # [IDF-0139] Envía solicitud a su controlador Cliente: Retirar saldo a una tarjeta del cliente
    def Retirar_Saldo(self, card, cardType):
        """
        Cliente: Retira saldo hacia una tarjeta.

        Parámetros:
            card (str): Número de tarjeta.
            cardType (str): Tipo de tarjeta.

        Retorna:
            bool: True si el retiro fue exitoso.
        """        
        return self.controller.Retirar_Saldo(card, cardType, self.id)


class Administrador(Usuario):
    def __init__(self, username, id):
        """
        Inicializa un objeto Administrador.

        Parámetros:
            username (str): Nombre de usuario.
            id (int): ID del administrador.
        """        
        super().__init__(user=username, id=id, ctr=C_Administrador())

    # [IDF-0140] Solicita lista a su controlador Administrador: Obtener lista de solicitudes de recargas pendientes
    def obtenerRecargas(self):
        """
        Administrador: Obtiene las solicitudes de recarga pendientes.

        Retorna:
            list: Lista de recargas pendientes.
        """        
        return self.controller.getRecargas()

    # [IDF-0141] Envía aprobación a su controlador Administrador: Aprobar la recarga de saldo de un cliente
    def aprobarSaldoCliente(self, id_recarga):
        """
        Administrador: Aprueba la recarga de saldo.

        Parámetros:
            id_recarga (int): ID de la recarga.
        """        
        self.controller.aprobarRecarga(id_recarga)

    # [IDF-0142] Envía datos a su controlador Administrador: Registrar nuevo contenido en el sistema
    def ingresarAgregarContenido(self, datos):
        """
        Administrador: Agrega un nuevo contenido al sistema.

        Parámetros:
            datos (dict): Información del contenido.
        """        
        self.controller.ingresarAgregarContenido(datos)

    # [IDF-0143] Envía datos a su controlador Administrador: Actualizar información de contenido existente
    def actualizarContenido(self, datos):
        """
        Administrador: Actualiza un contenido existente.

        Parámetros:
            datos (dict): Información actualizada.
        """        
        self.controller.actualizarContenido(datos)

    # [IDF-0144] Solicita información a su controlador Administrador: Buscar información general sobre contenidos
    def buscar_info(self, data):
        """
        Administrador: Busca información sobre contenidos.

        Parámetros:
            data (str): Parámetro de búsqueda.

        Retorna:
            list: Resultados encontrados.
        """        
        return self.controller.buscar_info(data)

    # [IDF-0145] Solicita selección a su controlador Administrador: Seleccionar un usuario específico por ID
    def seleccionar_user(self, id):
        """
        Administrador: Selecciona un usuario por ID.

        Parámetros:
            id (int): ID del usuario.

        Retorna:
            dict: Información del usuario.
        """     
        return self.controller.seleccionarUser(id)

    # [IDF-0146] Solicita datos a su controlador Administrador: Obtener descargas realizadas por un cliente
    def obtenerDescargasCliente(self, idU):
        """
        Administrador: Obtiene descargas realizadas por un cliente.

        Parámetros:
            idU (int): ID del cliente.

        Retorna:
            list: Lista de descargas.
        """    
        return self.controller.obtenerDescargasCliente(idU)
    
    # [IDF-0146] Solicita datos a su controlador Administrador: Obtener descargas realizadas por un cliente
    def obtenerDescargasClienteTime(self, idU):
        """
        Administrador: Obtiene descargas realizadas por un cliente.

        Parámetros:
            idU (int): ID del cliente.

        Retorna:
            list: Lista de descargas.
        """    
        return self.controller.obtenerDescargasClienteTime(idU)
    
    # [IDF-0147] Solicita datos a su controlador Administrador: Obtener recargas realizadas por un cliente
    def obtenerRecargasCliente(self, idU):
        """
        Administrador: Obtiene recargas realizadas por un cliente.

        Parámetros:
            idU (int): ID del cliente.

        Retorna:
            list: Lista de recargas.
        """ 
        return self.controller.obtenerRecargasCliente(idU)
    
    # [IDF-151] Envia id de un contenido para actulizar el estado de este al controlador Administrador.
    def actualizarEstadoContenido(self, idC):
        """
        Administrador: Actualiza el estado de un contenido.

        Parámetros:
            idC (int): ID del contenido.

        Retorna:
            bool: True si el estado fue actualizado correctamente.
        """   
        return self.controller.actualizarEstadoContenido(idC)
    
    # [IDF-158] Solicita al controlador Administrador todas las promociones actuales.
    def obtenerPromociones(self):
        """
        Administrador: Obtiene todas las promociones disponibles.

        Retorna:
            list: Lista de promociones.
        """
        return self.controller.obtenerPromociones()
    
    # [IDF-0168] El administrador enviar a su controlador administrador la asignacion de una promocion.
    def asignarPromocion(self, idC, idP):
        """
        Administrador: Asigna una promoción a un contenido.

        Parámetros:
            idC (int): ID del contenido.
            idP (int): ID de la promoción.

        Retorna:
            bool: True si se asignó correctamente.
        """        
        return self.controller.asignarPromocion(idC,idP)
    
    # [IDF-0173] El administrador enviar a su controlador administrador la agregación de una promocion.
    def agregarPromocion(self, data):
        """
        Administrador: Agrega una nueva promoción.

        Parámetros:
            data (dict): Datos de la promoción.

        Retorna:
            bool: True si se agregó exitosamente.
        """        
        return self.controller.agregarPromocion(data)
    
    # [IDF-0190] El administrador enviar a su controlador administrador obtencion de todas las categorías.
    def obtener_categorias(self):
        """
        Administrador: Obtiene todas las categorías existentes.

        Retorna:
            list: Lista de categorías.
        """        
        return self.controller.obtener_categorias()
    
    # [IDF-0183] El administrador enviar a su controlador administrador la agreasignacion de una categoría.
    def asignarCategoria(self, idC,idcat):
        """
        Administrador: Asigna una categoría a un contenido.

        Parámetros:
            idC (int): ID del contenido.
            idcat (int): ID de la categoría.

        Retorna:
            bool: True si se asignó correctamente.
        """        
        return self.controller.asignarCategoria(idC,idcat)
    
    # [IDF-0178] El administrador enviar a su controlador administrador la agregación de una categoría.
    def agregarCategoria(self, data):
        """
        Administrador: Agrega una nueva categoría al sistema.

        Parámetros:
            data (dict): Información de la categoría a agregar.

        Retorna:
            bool: True si la categoría fue agregada exitosamente.
        """        
        return self.controller.agregarCategoria(data)
    
    # [IDF-0207] El Administrador solcita obtener a su controlador el ranking de descargas de clientes.
    def obtenerRankingUsuariosPorDescargas(self):
        """
        Administrador: Solicita obtener el ranking de descargas de clientes.

        Retorna:
            list: Lista de clientes ordenado por el número de descargas.
        """            
        return self.controller.obtenerRankingUsuariosPorDescargas()
    
    # [IDF-0213] El Administrador solicita a su controlador Administrador la función de obtener la tabla de cierto tipo.
    def getTable(self, tipo):
        """
        Administrador: Solicita obtener cierta tabla de un tipo, pueden ser de usuarios, recargas, etc, según el tipo.

        Retorna:
            list: Lista de resultados de cierta tabla.
        """           
        return self.controller.getTable(tipo)