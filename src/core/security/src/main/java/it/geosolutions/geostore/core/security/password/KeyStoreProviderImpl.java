package it.geosolutions.geostore.core.security.password;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Enumeration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


import org.apache.log4j.Logger;
import org.springframework.beans.factory.BeanNameAware;

import static it.geosolutions.geostore.core.security.password.SecurityUtils.toBytes;

/**
 * Class for GeoStore specific key management
 * 
 * <strong>requires a  password</strong> form configuration
 * 
 * The type of the keystore is JCEKS and can be used/modified
 * with java tools like "keytool" from the command line.
 *  *  
 * 
 * @author Lorenzo Natali
 *
 */
public class KeyStoreProviderImpl implements BeanNameAware, KeyStoreProvider{
	 private static final Logger LOGGER = Logger.getLogger(KeyStoreProviderImpl.class);
    
    public final static String DEFAULT_BEAN_NAME="DefaultKeyStoreProvider";
    public final static String DEFAULT_FILE_NAME="geostore.jceks";
    public final static String PREPARED_FILE_NAME="geostore.jceks.new";
    
    public final static String CONFIGPASSWORDKEY = "ug:geostore:key";

    public final static String USERGROUP_PREFIX = "ug:";
    public final static String USERGROUP_POSTFIX = ":key";
    
    private String keyStoreFilePath = null;
    protected String name;
    protected File keyStoreFile;
    protected KeyStore ks;
	private char[] masterPassword;
	private String keyName;

	private MasterPasswordProvider masterPasswordProvider;
    public MasterPasswordProvider getMasterPasswordProvider() {
		return masterPasswordProvider;
	}

	public void setMasterPasswordProvider(
			MasterPasswordProvider masterPasswordProvider) {
		this.masterPasswordProvider = masterPasswordProvider;
	}

	public String getKeyName() {
		return keyName;
	}

	public void setKeyName(String keyName) {
		this.keyName = keyName;
	}


	public void setMasterPassword(char[] masterPassword) {
		this.masterPassword = masterPassword;
	}

	public final static String KEYSTORETYPE = "JCEKS";
     
    public KeyStoreProviderImpl()  {
    }

    @Override
    public void setBeanName(String name) {
        this.name = name;
    }


    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#getKeyStoreProvderFile()
     */
    @Override
    public File getFile() {
    	//retrieve the file on first access
        if (keyStoreFile == null) {
        	//if the keyStoreFilePath is configured create it
        	if(getKeyStoreFilePath()!=null){
        		keyStoreFile = new File(getKeyStoreFilePath());
        		if(keyStoreFile!=null){
        			if(!keyStoreFile.exists()){
        				if(keyStoreFile.isDirectory()){
        					keyStoreFile = new File(getKeyStoreFilePath()+ "geostore.jceks");
        				}
        				LOGGER.warn("the keyStore file doesn't exist. confiure a new one");
        			}
        		}
        		//if the file doesn't exist create a new one
        		//TODO add a key
        	//otherwise get the default one
        	}else{
            	URL defaultKeyStrore = KeyStoreProviderImpl.class.getClassLoader().getResource("geostore.jceks");
            	try {
            		if(defaultKeyStrore!= null){
            			keyStoreFile = new File(defaultKeyStrore.toURI());
            		}
				} catch (URISyntaxException e) {
					LOGGER.error("UNABLE TO GET THE DEFAULT KEY STORE");
				}
            }
        }
        
        return keyStoreFile;
    }

    public String getKeyStoreFilePath() {
		return keyStoreFilePath;
	}
    
    public void setKeyStoreFilePath(String keyStoreFilePath ){
    	this.keyStoreFilePath = keyStoreFilePath;
    }

	/* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#reloadKeyStore()
     */
    @Override
    public void reloadKeyStore() throws IOException{
        ks=null;
        assertActivatedKeyStore();
    }

    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#getKey(java.lang.String)
     */
    @Override
    public Key getKey(String alias) throws IOException{
        assertActivatedKeyStore();
        try {
            char[] passwd = getMasterPassword();
            try {
                return ks.getKey(alias, passwd);
            }
            finally {
               disposePassword(passwd);
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
    
    private char[] getMasterPassword() {
    	//TODO cifrate this password somehow
    	if( masterPassword !=null){
    		return masterPassword;
    	}else{
    		if(masterPasswordProvider != null){
    			try {
					masterPassword = masterPasswordProvider.doGetMasterPassword();
				} catch (Exception e) {
					LOGGER.error("unable to read the master password\n:" + e.getStackTrace());
				}
    			
    		}
    	}
    	return masterPassword;
	}
    public Enumeration<String> aliases(){
    	if(ks!=null)
			try {
				return ks.aliases();
			} catch (KeyStoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
    	return null;
    	
    }

	/* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#getConfigPasswordKey()
     */
    @Override
    public byte[] getConfigPasswordKey() throws IOException{
        SecretKey key = getSecretKey(CONFIGPASSWORDKEY);
        if (key==null) return null;
        return key.getEncoded();
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#hasConfigPasswordKey()
     */
    @Override
    public boolean hasConfigPasswordKey() throws IOException {
        return containsAlias(CONFIGPASSWORDKEY);
    }
    
        
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#containsAlias(java.lang.String)
     */
    @Override
    public boolean containsAlias(String alias) throws IOException{
        assertActivatedKeyStore();
        try {
            return ks.containsAlias(alias);
        } catch (KeyStoreException e) {
            throw new IOException(e);
        }
    }
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#getUserGRoupKey(java.lang.String)
     */
    @Override
    public byte[] getUserGroupKey(String serviceName) throws IOException{
        SecretKey key = getSecretKey(aliasForGroupService(serviceName));
        if (key==null) return null;
        return key.getEncoded();

    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#hasUserGRoupKey(java.lang.String)
     */
    @Override
    public boolean hasUserGroupKey(String serviceName) throws IOException {
        return containsAlias(aliasForGroupService(serviceName));
        
    }

    
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#getSecretKey(java.lang.String)
     */
    @Override
    public SecretKey getSecretKey(String name) throws IOException{
        Key key = getKey(name);
        if (key==null) return null;
        if ((key instanceof SecretKey) == false)
            throw new IOException("Invalid key type for: "+name);
        return (SecretKey) key;
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#getPublicKey(java.lang.String)
     */
    @Override
    public PublicKey getPublicKey(String name) throws IOException{
        Key key = getKey(name);
        if (key==null) return null;
        if ((key instanceof PublicKey) == false)
            throw new IOException("Invalid key type for: "+name);
        return (PublicKey) key;
    }

    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#getPrivateKey(java.lang.String)
     */
    @Override
    public PrivateKey getPrivateKey(String name) throws IOException{
        Key key = getKey(name);
        if (key==null) return null;
        if ((key instanceof PrivateKey) == false)
            throw new IOException("Invalid key type for: "+name);
        return (PrivateKey) key;
    }

    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#aliasForGroupService(java.lang.String)
     */
    @Override
    public String aliasForGroupService(String serviceName) {
        StringBuffer buff = new StringBuffer(USERGROUP_PREFIX);
        buff.append(serviceName);
        buff.append(USERGROUP_POSTFIX);
        return buff.toString();            
    }
    
    /**
     * Opens or creates a {@link KeyStore} using the file
     * {@link #DEFAULT_FILE_NAME}
     * 
     * Throws an exception for an invalid master key
     * 
     * @throws IOException 
     */            
    protected void assertActivatedKeyStore() throws IOException {
        if (ks != null) 
            return;
        
        char[] passwd = getMasterPassword();
        try {
            ks = KeyStore.getInstance(KEYSTORETYPE);
            if (getFile().exists()==false) { // create an empy one
                ks.load(null, passwd);
                addInitialKeys();
                FileOutputStream fos = new FileOutputStream(getFile());
                ks.store(fos, passwd);
                fos.close();
            } else {
                FileInputStream fis =
                        new FileInputStream(getFile());
                ks.load(fis, passwd);
                fis.close();
            }
        } catch (Exception ex) {
            if (ex instanceof IOException) // avoid useless wrapping
                throw (IOException) ex;
            throw new IOException (ex);
        }
        finally {
            disposePassword(passwd);
        }
    }
    
    private void disposePassword(char[] passwd) {
		// TODO implement it when security improved.
		
	}

	/* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#isKeystorePassword(java.lang.String)
     */
    @Override
    public boolean isKeyStorePassword(char[] password) throws IOException{
        if (password==null) return false;
        assertActivatedKeyStore();
        
        KeyStore testStore=null;
        try {
            testStore = KeyStore.getInstance(KEYSTORETYPE);
        } catch (KeyStoreException e1) {
            // should not happen, see assertActivatedKeyStore
            throw new RuntimeException(e1);
        }
        FileInputStream fis =
                new FileInputStream(getFile());
        try {
            testStore.load(fis, password);
        } catch (IOException e2) {
            // indicates invalid password
            return false;
        } catch (Exception e) {
            // should not happen, see assertActivatedKeyStore
            throw new RuntimeException(e);
        }                
        fis.close();     
        return true;
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#setSecretKey(java.lang.String, java.lang.String)
     */
    @Override
    public void setSecretKey(String alias, char[] key) throws IOException {
        assertActivatedKeyStore();
        SecretKey mySecretKey=new SecretKeySpec(toBytes(key),"PBE");
        KeyStore.SecretKeyEntry skEntry =
            new KeyStore.SecretKeyEntry(mySecretKey);
        char[] passwd = getMasterPassword();
        try {
            ks.setEntry(alias, skEntry, new KeyStore.PasswordProtection(passwd));
        } catch (KeyStoreException e) {
            throw new IOException(e);
        }
        finally {
            disposePassword(passwd);
        }
    }

    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#setUserGroupKey(java.lang.String, java.lang.String)
     */
    @Override
    public void setUserGroupKey(String serviceName,char[] password) throws IOException{
        String alias = aliasForGroupService(serviceName);
        setSecretKey(alias, password);
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#removeKey(java.lang.String)
     */
    @Override
    public void removeKey(String alias ) throws IOException {
        assertActivatedKeyStore();
        try {
            ks.deleteEntry(alias);
        } catch (KeyStoreException e) {
            throw new IOException(e);
        }
    }

    
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#storeKeyStore()
     */
    @Override
    public void storeKeyStore() throws IOException{
        // store away the keystore
        assertActivatedKeyStore();
        FileOutputStream fos = new  FileOutputStream(getFile());

        char[] passwd = getMasterPassword(); 
        try {
            ks.store(fos, passwd);
        } catch (Exception e) {
            throw new IOException(e);
        }
        finally {
            disposePassword(passwd);
        }
        fos.close();
    }
    
    /**
     * Creates initial key entries
     * auto generated keys
     * {@link #CONFIGPASSWORDKEY}
     * 
     * @throws IOException
     */
    protected void addInitialKeys() throws IOException {

        RandomPasswordProvider randPasswdProvider = 
                getRandomPassworddProvider(); 
        
        char[] configKey = randPasswdProvider.getRandomPasswordWithDefaultLength();
        setSecretKey( CONFIGPASSWORDKEY, configKey);
    }
    
    private RandomPasswordProvider getRandomPassworddProvider() {
		return new RandomPasswordProvider();
	}

	/* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#prepareForMasterPasswordChange(java.lang.String, java.lang.String)
     */
    @Override
    public void prepareForMasterPasswordChange(char[] oldPassword, char[] newPassword) throws IOException{

                
        File dir = getFile().getParentFile();
        File newKSFile = new File(dir,PREPARED_FILE_NAME);
        if (newKSFile.exists())
            newKSFile.delete();
        
        try {
            KeyStore oldKS=KeyStore.getInstance(KEYSTORETYPE);
            FileInputStream fin = new FileInputStream(getFile());
            oldKS.load(fin, oldPassword);
            fin.close();
            
            KeyStore newKS = KeyStore.getInstance(KEYSTORETYPE);
            newKS.load(null, newPassword);
            KeyStore.PasswordProtection protectionparam = 
                    new KeyStore.PasswordProtection(newPassword);

            Enumeration<String> enumeration = oldKS.aliases();
            while (enumeration.hasMoreElements()) {
                String alias =enumeration.nextElement();
                Key key = oldKS.getKey(alias, oldPassword);
                KeyStore.Entry entry =null;
                if (key instanceof SecretKey) 
                    entry = new KeyStore.SecretKeyEntry((SecretKey)key);
                if (key instanceof PrivateKey) 
                    entry = new KeyStore.PrivateKeyEntry((PrivateKey)key,
                            oldKS.getCertificateChain(alias));                         
                if (key instanceof PublicKey) 
                    entry = new KeyStore.TrustedCertificateEntry(oldKS.getCertificate(alias));                         
                if (entry == null)
                    LOGGER.warn("Unknown key in store, alias: "+alias+
                            " class: "+ key.getClass().getName());
                else
                    newKS.setEntry(alias, entry, protectionparam);
            }            
           
            FileOutputStream fos = new FileOutputStream(newKSFile);                    
            newKS.store(fos, newPassword);
            fos.close();

        } catch (Exception ex) {
            throw new IOException(ex);
        }

    }

    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#abortMasterPasswordChange()
     */
    @Override
    public void abortMasterPasswordChange() {
        File dir = getFile().getParentFile();
        File newKSFile = new File(dir,PREPARED_FILE_NAME);
        if (newKSFile.exists()) {
            //newKSFile.delete();
        }
        
    }
    
    
    /* (non-Javadoc)
     * @see org.geoserver.security.password.KeystoreProvider#commitMasterPasswordChange()
     */
    @Override
    public void commitMasterPasswordChange() throws IOException {
        File dir = getFile().getParentFile();
        File newKSFile = new File(dir,PREPARED_FILE_NAME);
        File oldKSFile = new File(dir,DEFAULT_FILE_NAME);
        
        if (newKSFile.exists()==false)
            return; //nothing to do

        if (oldKSFile.exists()==false)
            return; //not initialized
        
        // Try to open with new password
        FileInputStream fin = new FileInputStream(newKSFile);
        char[] passwd = getMasterPassword();
        
        try {
            KeyStore newKS = KeyStore.getInstance(KEYSTORETYPE);
            newKS.load(fin, passwd);
            
            // to be sure, decrypt all keys
            Enumeration<String> enumeration = newKS.aliases();
            while (enumeration.hasMoreElements()) {
                newKS.getKey(enumeration.nextElement(), passwd);
            }            
            fin.close();
            fin=null;
            if (oldKSFile.delete()==false) { 
                LOGGER.error("cannot delete " +getFile().getCanonicalPath());
                return;
            }
            
            if (newKSFile.renameTo(oldKSFile)==false) {
                String msg = "cannot rename "+ newKSFile.getCanonicalPath();
                msg += "to " + oldKSFile.getCanonicalPath();
                msg += "Try to rename manually and restart";
                LOGGER.error(msg);
                return;
            }
            reloadKeyStore();
            LOGGER.info("Successfully changed master password");            
        } catch (IOException e) {
            String msg = "Error creating new keystore: " + newKSFile.getCanonicalPath();
            LOGGER.warn( msg, e);
            throw e;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        finally {
            disposePassword(passwd);
            if (fin != null) {
               try{ 
                   fin.close();
                   } 
                catch (IOException ex) {
                    // give up
                }
            }
        }
        
    }
}
